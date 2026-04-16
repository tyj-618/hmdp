package hmdp.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import hmdp.dto.Result;
import hmdp.entity.SeckillVoucher;
import hmdp.entity.VoucherOrder;
import hmdp.mapper.VoucherOrderMapper;
import hmdp.service.ISeckillVoucherService;
import hmdp.service.IVoucherOrderService;
import hmdp.utils.UserHolder;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Resource
    private ISeckillVoucherService seckillVoucherService;

    @Override
    @Transactional
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

        //4.判断库存是否充足
        //5.扣减库存
        //这两部通过乐观式写法来完成
        boolean success = seckillVoucherService.update()
                .setSql("stock = stock - 1")
                .eq("voucher_id", voucherId)
                .gt("stock", 0)
                .update();

        if(!success){
            return Result.fail("库存不足！");
        }

        //6.创建订单并保存
        VoucherOrder order = new VoucherOrder();
        Long userId = UserHolder.getUser().getId();
        order.setUserId(userId);
        order.setVoucherId(voucherId);
        save(order);

        return Result.ok(order.getId());
    }
}
