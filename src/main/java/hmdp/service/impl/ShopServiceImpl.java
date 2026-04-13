package hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import hmdp.dto.Result;
import hmdp.entity.Shop;
import hmdp.mapper.ShopMapper;
import hmdp.service.IShopService;
import jakarta.annotation.Resource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.TimeUnit;

import static hmdp.utils.RedisConstants.*;

@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /*@Override
    public Result queryById(Long id) {
        String key = CACHE_SHOP_KEY + id;

        //1.查Redis
        String shopJson = stringRedisTemplate.opsForValue().get(key);

        //2.Redis中有正常数据
        if (StrUtil.isNotBlank(shopJson)) {
            Shop shop = JSONUtil.toBean(shopJson, Shop.class);
            return Result.ok(shop);
        }

        //3.Redis 中是空字符串，说明数据库里也没有
        if (shopJson != null) {
            return Result.fail("店铺不存在！");
        }

        //4.Redis 没有，查数据库
        Shop shop = getById(id);

        //5.数据库没有，写入空值，防止缓存穿透
        if (shop == null) {
            stringRedisTemplate.opsForValue()
                    .set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
            return Result.fail("店铺不存在！");
        }

        //6.数据库有，写入Redis
        stringRedisTemplate.opsForValue()
                .set(key, JSONUtil.toJsonStr(shop), CACHE_SHOP_TTL, TimeUnit.MINUTES);

        //7.返回
        return Result.ok(shop);

    }*/
    @Override
    public Result queryById(Long id) {
        Shop shop = queryWithMutex(id);
        if (shop == null) {
            return Result.fail("店铺不存在！");
        }
        return Result.ok(shop);
    }

    public Shop queryWithMutex(Long id) {
        String key = CACHE_SHOP_KEY + id;

        //1.查Redis
        String shopJson = stringRedisTemplate.opsForValue().get(key);

        //2.命中正常缓存
        if (StrUtil.isNotBlank(shopJson)) {
            return JSONUtil.toBean(shopJson, Shop.class);
        }

        //3.命中空值缓存
        if (shopJson != null) {
            return null;
        }

        String lockKey = LOCK_SHOP_KEY + id;
        Shop shop = null;
        boolean isLock = false;

        try {
            //4.获取互斥锁
            isLock = tryLock(lockKey);

            //5.获取失败，休眠并重试
            if (!isLock) {
                Thread.sleep(50);
                return queryWithMutex(id);
            }

            //6.获取锁成功后，再次检查Redis（双重检查）
            shopJson = stringRedisTemplate.opsForValue().get(key);

            if (StrUtil.isNotBlank(shopJson)) {
                return JSONUtil.toBean(shopJson, Shop.class);
            }

            if (shopJson != null) {
                return null;
            }

            //7.查数据库
            System.out.println("查询数据库...");
            shop = getById(id);

            //8.数据库不存在，写空值
            if (shop == null) {
                stringRedisTemplate.opsForValue()
                        .set(key, "", CACHE_SHOP_TTL, TimeUnit.MINUTES);
                return null;
            }

            //9.数据库存在，写入Redis
            stringRedisTemplate.opsForValue()
                    .set(key, JSONUtil.toJsonStr(shop), CACHE_SHOP_TTL, TimeUnit.MINUTES);
            return shop;
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            //10.释放锁
            if (isLock) {
                unlock(lockKey);
            }
        }
    }

    private boolean tryLock(String key) {
        Boolean flag = stringRedisTemplate.opsForValue()
                .setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }

    private void unlock(String key) {
        stringRedisTemplate.delete(key);
    }

    @Transactional
    @Override
    public Result updateShop(Shop shop) {
        Long id= shop.getId();
        if (id == null) {
            return Result.fail("店铺id不能为空");
        }

        updateById(shop);
        stringRedisTemplate.delete(CACHE_SHOP_KEY + id);

        return Result.ok();
    }
}
