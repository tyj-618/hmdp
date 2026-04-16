package hmdp.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import hmdp.entity.Voucher;

import java.util.List;

public interface VoucherMapper extends BaseMapper<Voucher> {

    List<Voucher> queryVoucherOfShop(Long shopId);
}
