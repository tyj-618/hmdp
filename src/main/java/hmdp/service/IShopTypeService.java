package hmdp.service;

import com.baomidou.mybatisplus.extension.service.IService;
import hmdp.dto.Result;
import hmdp.entity.ShopType;

public interface IShopTypeService extends IService<ShopType> {
    Result queryTypeList();
}
