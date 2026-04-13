package hmdp.service;

import com.baomidou.mybatisplus.extension.service.IService;
import hmdp.dto.Result;
import hmdp.entity.Shop;
import org.springframework.transaction.annotation.Transactional;

public interface IShopService extends IService<Shop> {
    Result queryById(Long id);

    @Transactional
    Result updateShop(Shop shop);
}
