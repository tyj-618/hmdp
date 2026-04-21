package hmdp.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import hmdp.dto.Result;
import hmdp.entity.SeckillVoucher;
import hmdp.entity.Voucher;
import hmdp.mapper.VoucherMapper;
import hmdp.service.ISeckillVoucherService;
import hmdp.service.IVoucherService;
import jakarta.annotation.Resource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class VoucherServiceImpl extends ServiceImpl<VoucherMapper, Voucher> implements IVoucherService {

    @Resource
    private ISeckillVoucherService seckillVoucherService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result queryVoucherOfShop(Long shopId) {
        List<Voucher> vouchers = getBaseMapper().queryVoucherOfShop(shopId);
        return Result.ok(vouchers);
    }

    @Override
    @Transactional
    public void addSeckillVoucher(Voucher voucher) {
        //1.保存优惠券基本信息到 tb_voucher
        save(voucher);

        //2.组装秒杀优惠券信息
        SeckillVoucher seckillVoucher = new SeckillVoucher();
        seckillVoucher.setVoucherId(voucher.getId());
        seckillVoucher.setStock(voucher.getStock());
        seckillVoucher.setBeginTime(voucher.getBeginTime());
        seckillVoucher.setEndTime(voucher.getEndTime());

        //3.保存秒杀信息到 tb_seckill_voucher
        seckillVoucherService.save(seckillVoucher);

        //4.保存到Redis
        stringRedisTemplate.opsForValue().set(
                "seckill:stock:" + voucher.getId(),
                voucher.getStock().toString()
        );
    }
}
