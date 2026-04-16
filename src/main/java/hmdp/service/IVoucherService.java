package hmdp.service;

import com.baomidou.mybatisplus.extension.service.IService;
import hmdp.dto.Result;
import hmdp.entity.Voucher;

public interface IVoucherService extends IService<Voucher> {

    Result queryVoucherOfShop(Long shopId);

    void addSeckillVoucher(Voucher voucher);
}
