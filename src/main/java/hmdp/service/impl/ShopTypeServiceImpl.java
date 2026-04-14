package hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import hmdp.dto.Result;
import hmdp.entity.ShopType;
import hmdp.mapper.ShopTypeMapper;
import hmdp.service.IShopTypeService;
import jakarta.annotation.Resource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static hmdp.utils.RedisConstants.CACHE_SHOP_TYPE_KEY;

@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result queryTypeList() {
        String key = CACHE_SHOP_TYPE_KEY;

        //1.查Redis
        String typeJson = stringRedisTemplate.opsForValue().get(key);

        //2.Redis命中，直接返回
        if (StrUtil.isNotBlank(typeJson)) {
            List<ShopType> typeList = JSONUtil.toList(typeJson, ShopType.class);
            return Result.ok(typeList);
        }

        //3.Redis未命中，查数据库
        List<ShopType> typeList = query().orderByAsc("sort").list();

        //4.数据库为空
        if (typeList == null || typeList.isEmpty()) {
            return Result.fail("店铺分类不存在！");
        }

        //5.写入Redis
        stringRedisTemplate.opsForValue().set(
                key,
                JSONUtil.toJsonStr(typeList),
                30L,
                TimeUnit.MINUTES
        );

        //6.返回
        return Result.ok(typeList);
    }
}
