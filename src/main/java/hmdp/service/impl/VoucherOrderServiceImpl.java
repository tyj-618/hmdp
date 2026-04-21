package hmdp.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import hmdp.dto.Result;
import hmdp.entity.SeckillVoucher;
import hmdp.entity.VoucherOrder;
import hmdp.mapper.VoucherOrderMapper;
import hmdp.service.ISeckillVoucherService;
import hmdp.service.IVoucherOrderService;
import hmdp.utils.RedisIdWorker;
import hmdp.utils.UserHolder;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder>
        implements IVoucherOrderService {

    private static final String QUEUE_NAME = "stream.orders";
    private static final String GROUP_NAME = "g1";
    private static final String CONSUMER_NAME = "c1";

    @Resource
    private ISeckillVoucherService seckillVoucherService;

    @Resource
    private RedisIdWorker redisIdWorker;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private RedissonClient redissonClient;

    private IVoucherOrderService proxy;

    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;

    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
    }

    private static final ExecutorService SECKILL_ORDER_EXECUTOR = Executors.newSingleThreadExecutor();

    @PostConstruct
    private void init() {
        try {
            Boolean hasKey = stringRedisTemplate.hasKey(QUEUE_NAME);
            if (Boolean.FALSE.equals(hasKey)) {
                Map<String, String> initMap = new HashMap<>();
                initMap.put("init", "0");
                stringRedisTemplate.opsForStream()
                        .add(StreamRecords.mapBacked(initMap).withStreamKey(QUEUE_NAME));
            }

            try {
                stringRedisTemplate.opsForStream()
                        .createGroup(QUEUE_NAME, ReadOffset.latest(), GROUP_NAME);
            } catch (Exception e) {
                if (e.getMessage() == null || !e.getMessage().contains("BUSYGROUP")) {
                    throw e;
                }
            }
        } catch (Exception e) {
            log.error("初始化 stream 消费组异常", e);
        }

        SECKILL_ORDER_EXECUTOR.submit(new VoucherOrderHandler());
    }

    private class VoucherOrderHandler implements Runnable {
        @Override
        public void run() {
            while (true) {
                try {
                    List<MapRecord<String, Object, Object>> list = stringRedisTemplate.opsForStream().read(
                            Consumer.from(GROUP_NAME, CONSUMER_NAME),
                            StreamReadOptions.empty().count(1).block(Duration.ofSeconds(2)),
                            StreamOffset.create(QUEUE_NAME, ReadOffset.lastConsumed())
                    );

                    if (list == null || list.isEmpty()) {
                        continue;
                    }

                    MapRecord<String, Object, Object> record = list.get(0);
                    VoucherOrder voucherOrder = parseVoucherOrder(record);

                    handleVoucherOrder(voucherOrder);

                    stringRedisTemplate.opsForStream()
                            .acknowledge(QUEUE_NAME, GROUP_NAME, record.getId());
                } catch (Exception e) {
                    log.error("处理订单消息异常", e);
                    handlePendingList();
                }
            }
        }
    }

    private void handlePendingList() {
        while (true) {
            try {
                List<MapRecord<String, Object, Object>> list = stringRedisTemplate.opsForStream().read(
                        Consumer.from(GROUP_NAME, CONSUMER_NAME),
                        StreamReadOptions.empty().count(1),
                        StreamOffset.create(QUEUE_NAME, ReadOffset.from("0"))
                );

                if (list == null || list.isEmpty()) {
                    break;
                }

                MapRecord<String, Object, Object> record = list.get(0);
                VoucherOrder voucherOrder = parseVoucherOrder(record);

                handleVoucherOrder(voucherOrder);

                stringRedisTemplate.opsForStream()
                        .acknowledge(QUEUE_NAME, GROUP_NAME, record.getId());
            } catch (Exception e) {
                log.error("处理 pending-list 订单异常", e);
                try {
                    Thread.sleep(20);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
    }

    private VoucherOrder parseVoucherOrder(MapRecord<String, Object, Object> record) {
        Map<Object, Object> value = record.getValue();
        VoucherOrder voucherOrder = new VoucherOrder();
        voucherOrder.setId(Long.valueOf(value.get("id").toString()));
        voucherOrder.setUserId(Long.valueOf(value.get("userId").toString()));
        voucherOrder.setVoucherId(Long.valueOf(value.get("voucherId").toString()));
        return voucherOrder;
    }

    private void handleVoucherOrder(VoucherOrder voucherOrder) {
        Long userId = voucherOrder.getUserId();
        Long voucherId = voucherOrder.getVoucherId();

        RLock lock = redissonClient.getLock("lock:order:" + userId);
        boolean isLock = lock.tryLock();
        if (!isLock) {
            log.error("不允许重复下单，userId={}, voucherId={}", userId, voucherId);
            return;
        }

        try {
            proxy.createVoucherOrder(voucherOrder);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public Result seckillVoucher(Long voucherId) {
        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
        if (voucher == null) {
            return Result.fail("优惠券不存在！");
        }

        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(voucher.getBeginTime())) {
            return Result.fail("秒杀活动还未开始！");
        }
        if (now.isAfter(voucher.getEndTime())) {
            return Result.fail("秒杀活动已经结束！");
        }

        Long userId = UserHolder.getUser().getId();
        long orderId = redisIdWorker.nextId("order");

        // 保持当前稳定方案
        this.proxy = (IVoucherOrderService) AopContext.currentProxy();

        Long result = stringRedisTemplate.execute(
                SECKILL_SCRIPT,
                Arrays.asList(
                        "seckill:stock:" + voucherId,
                        "seckill:order:" + voucherId,
                        QUEUE_NAME
                ),
                userId.toString(),
                voucherId.toString(),
                String.valueOf(orderId)
        );

        if (result == null) {
            return Result.fail("下单失败，请重试！");
        }

        int r = result.intValue();
        if (r != 0) {
            return Result.fail(r == 1 ? "库存不足！" : "不能重复下单！");
        }

        return Result.ok(orderId);
    }

    @Override
    @Transactional
    public void createVoucherOrder(VoucherOrder voucherOrder) {
        Long userId = voucherOrder.getUserId();
        Long voucherId = voucherOrder.getVoucherId();

        long count = query()
                .eq("user_id", userId)
                .eq("voucher_id", voucherId)
                .count();

        if (count > 0) {
            log.error("用户已经购买过一次，userId={}, voucherId={}", userId, voucherId);
            return;
        }

        boolean success = seckillVoucherService.update()
                .setSql("stock = stock - 1")
                .eq("voucher_id", voucherId)
                .gt("stock", 0)
                .update();

        if (!success) {
            log.error("库存不足，voucherId={}", voucherId);
            return;
        }

        save(voucherOrder);
    }
}