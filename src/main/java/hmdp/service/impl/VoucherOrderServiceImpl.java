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
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.connection.stream.StreamRecords;
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

import static hmdp.utils.RedisConstants.*;

@Slf4j
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder>
        implements IVoucherOrderService {

    @Resource
    private ISeckillVoucherService seckillVoucherService;

    @Resource
    private RedisIdWorker redisIdWorker;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private RedissonClient redissonClient;

    private IVoucherOrderService proxy;

    // Lua脚本
    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;

    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
    }

    // 单线程线程池
    private static final ExecutorService SECKILL_ORDER_EXECUTOR = Executors.newSingleThreadExecutor();

    // 项目启动后就开启异步处理线程
    @PostConstruct
    private void init() {
        try {
            // 如果 Stream 不存在，先创建一个空消息，避免后续创建消费组失败
            Boolean hasKey = stringRedisTemplate.hasKey(STREAM_ORDERS);
            if (Boolean.FALSE.equals(hasKey)) {
                Map<String, String> initMap = new HashMap<>();
                initMap.put("init", "0");
                stringRedisTemplate.opsForStream()
                        .add(StreamRecords.mapBacked(initMap).withStreamKey(STREAM_ORDERS));
            }

            // 创建消费组，已存在则忽略
            try {
                stringRedisTemplate.opsForStream()
                        .createGroup(STREAM_ORDERS, ReadOffset.latest(), STREAM_GROUP);
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

    private void handlePendingList() {
        while (true) {
            try {
                // 1. 读取 pending-list 中的消息
                List<MapRecord<String, Object, Object>> list = stringRedisTemplate.opsForStream().read(
                        Consumer.from(STREAM_GROUP, STREAM_CONSUMER),
                        StreamReadOptions.empty().count(1),
                        StreamOffset.create(STREAM_ORDERS, ReadOffset.from("0"))
                );

                // 2. 判断是否读取成功
                if (list == null || list.isEmpty()) {
                    break;
                }

                // 3. 解析消息
                MapRecord<String, Object, Object> record = list.get(0);
                VoucherOrder voucherOrder = parseVoucherOrder(record);

                // 4. 处理订单
                handleVoucherOrder(voucherOrder);

                // 5. ACK确认
                stringRedisTemplate.opsForStream()
                        .acknowledge(STREAM_ORDERS, STREAM_GROUP, record.getId());

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

    // 后台异步处理类
    private class VoucherOrderHandler implements Runnable {
        @Override
        public void run() {
            while (true) {
                try {
                    // 1. 读取消息队列中的订单信息
                    List<MapRecord<String, Object, Object>> list = stringRedisTemplate.opsForStream().read(
                            Consumer.from(STREAM_GROUP, STREAM_CONSUMER),
                            StreamReadOptions.empty().count(1).block(Duration.ofSeconds(2)),
                            StreamOffset.create(STREAM_ORDERS, ReadOffset.lastConsumed())
                    );

                    // 2. 判断是否读取成功
                    if (list == null || list.isEmpty()) {
                        continue;
                    }

                    // 3. 解析消息
                    MapRecord<String, Object, Object> record = list.get(0);
                    VoucherOrder voucherOrder = parseVoucherOrder(record);

                    // 4. 处理订单
                    handleVoucherOrder(voucherOrder);

                    // 5. ACK确认
                    stringRedisTemplate.opsForStream()
                            .acknowledge(STREAM_ORDERS, STREAM_GROUP, record.getId());

                } catch (Exception e) {
                    log.error("处理订单消息异常", e);
                    handlePendingList();
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

    // 真正处理订单
    private void handleVoucherOrder(VoucherOrder voucherOrder) {
        Long userId = voucherOrder.getUserId();
        Long voucherId = voucherOrder.getVoucherId();

        // 一人一单分布式锁
        RLock lock = redissonClient.getLock(LOCK_ORDER_KEY + userId);
        boolean isLock = lock.tryLock();
        if (!isLock) {
            log.error("不允许重复下单，userId={}, voucherId={}", userId, voucherId);
            return;
        }

        try {
            // 通过代理对象调用事务方法
            proxy.createVoucherOrder(voucherOrder);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public Result seckillVoucher(Long voucherId) {
        // 1. 根据 voucherId 查询秒杀券
        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
        if (voucher == null) {
            return Result.fail("优惠券不存在！");
        }

        // 2. 判断秒杀是否开始
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(voucher.getBeginTime())) {
            return Result.fail("秒杀活动还未开始！");
        }

        // 3. 判断秒杀是否结束
        if (now.isAfter(voucher.getEndTime())) {
            return Result.fail("秒杀活动已经结束！");
        }

        // 4. 获取当前用户
        Long userId = UserHolder.getUser().getId();

        // 5. 生成订单 id
        long orderId = redisIdWorker.nextId("order");

        // 6. 在请求线程里拿到代理对象
        //通过当前代理对象调用事务方法，保证 @Transactional 生效
        this.proxy = (IVoucherOrderService) AopContext.currentProxy();

        // 7. 执行 Lua 脚本，做 Redis 原子校验
        Long result = stringRedisTemplate.execute(
                SECKILL_SCRIPT,
                Arrays.asList(
                        SECKILL_STOCK_KEY + voucherId,
                        SECKILL_ORDER_KEY + voucherId,
                        STREAM_ORDERS
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

        // 8. 返回订单 id
        return Result.ok(orderId);
    }

    @Override
    @Transactional
    public void createVoucherOrder(VoucherOrder voucherOrder) {
        Long userId = voucherOrder.getUserId();
        Long voucherId = voucherOrder.getVoucherId();

        // 1. 一人一单
        long count = query()
                .eq("user_id", userId)
                .eq("voucher_id", voucherId)
                .count();

        if (count > 0) {
            log.error("用户已经购买过一次，userId={}, voucherId={}", userId, voucherId);
            return;
        }

        // 2. 扣减库存
        boolean success = seckillVoucherService.update()
                .setSql("stock = stock - 1")
                .eq("voucher_id", voucherId)
                .gt("stock", 0)
                .update();

        if (!success) {
            log.error("库存不足，voucherId={}", voucherId);
            return;
        }

        // 3. 保存订单
        save(voucherOrder);
        log.info("订单创建成功, orderId={}, userId={}, voucherId={}",
                voucherOrder.getId(), userId, voucherId);
    }
}