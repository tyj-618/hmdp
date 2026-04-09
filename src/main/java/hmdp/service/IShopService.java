package hmdp.service;

import com.baomidou.mybatisplus.extension.service.IService;
import hmdp.dto.Result;
import hmdp.entity.Shop;

public interface IShopService extends IService<Shop> {
    Result queryById(Long id);
}
