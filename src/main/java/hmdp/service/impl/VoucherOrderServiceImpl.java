package hmdp.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import hmdp.dto.Result;
import hmdp.entity.SeckillVoucher;
import hmdp.entity.VoucherOrder;
import hmdp.mapper.VoucherOrderMapper;
import hmdp.service.ISeckillVoucherService;
import hmdp.service.IVoucherOrderService;
import hmdp.utils.ILock;
import hmdp.utils.RedisIdWorker;
import hmdp.utils.SimpleRedisLock;
import hmdp.utils.UserHolder;
import jakarta.annotation.Resource;
import org.springframework.aop.framework.AopContext;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Resource
    private ISeckillVoucherService seckillVoucherService;

    @Resource
    private RedisIdWorker redisIdWorker;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result seckillVoucher(Long voucherId) {

        //1.根据voucherId查询秒杀券
        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
        if(voucher == null){
            return Result.fail("优惠券不存在！");
        }

        //2.判断秒杀是否开始
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(voucher.getBeginTime())) {
            return Result.fail("秒杀活动还未开始！");
        }

        //3.判断秒杀是否结束
        if (now.isAfter(voucher.getEndTime())) {
            return Result.fail("秒杀活动已经结束！");
        }

        //4.获取当前用户
        Long userId = UserHolder.getUser().getId();

        //5.获取代理对象
        IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();

        /*//6.给当前用户加锁
        synchronized (userId.toString().intern()) {
            return proxy.createVoucherOrder(voucherId);
        }*/

        //6.创建分布式锁对象
        ILock lock = new SimpleRedisLock("order:" + userId, stringRedisTemplate);

        //7.尝试获取锁
        boolean isLock = lock.tryLock(1200);
        if (!isLock) {
            return Result.fail("不允许重复下单！");
        }

        try {
            return proxy.createVoucherOrder(voucherId);
        } finally {
            lock.unlock();
        }
    }

    @Override
    @Transactional
    public Result createVoucherOrder(Long voucherId) {
        Long userId = UserHolder.getUser().getId();

        //1.一人一单
        long count = query()
                .eq("user_id", userId)
                .eq("voucher_id", voucherId)
                .count();

        if(count > 0){
            return Result.fail("该用户已经购买过一次！");
        }

        //2.减扣库存
        boolean success = seckillVoucherService.update()
                .setSql("stock = stock - 1")
                .eq("voucher_id", voucherId)
                .gt("stock", 0)
                .update();

        if (!success) {
            return Result.fail("库存不足！");
        }

        //3.创建订单
        VoucherOrder order = new VoucherOrder();
        long orderId = redisIdWorker.nextId("order");
        order.setId(orderId);
        order.setUserId(userId);
        order.setVoucherId(voucherId);

        save(order);

        return Result.ok(order.getId());
    }
}
