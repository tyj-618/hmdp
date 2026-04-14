package hmdp;

import hmdp.entity.Shop;
import hmdp.service.impl.ShopServiceImpl;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;

@SpringBootTest
class HmdpApplicationTests {

    @Resource
    private ShopServiceImpl shopService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Test
    void testSaveShop2Redis() {
        shopService.saveShop2Redis(1L, 30L);

        String key = "cache:shop:1";
        String json = stringRedisTemplate.opsForValue().get(key);
        System.out.println("Redis中的值：" + json);
    }

    @Test
    void testQueryWithLogicalExpireNotExpired() {
        // 先预热，设置30秒逻辑过期
        shopService.saveShop2Redis(1L, 30L);

        // 立刻查询，此时还没过期
        Shop shop = shopService.queryWithLogicalExpire(1L);
        System.out.println("未过期时查询结果：" + shop);
    }

    @Test
    void testQueryWithLogicalExpireExpired() throws Exception {
        // 1. 先预热，设置5秒逻辑过期，方便测试
        shopService.saveShop2Redis(1L, 5L);

        String key = "cache:shop:1";

        // 2. 先读一次，看看旧的 expireTime
        String oldJson = stringRedisTemplate.opsForValue().get(key);
        System.out.println("过期前Redis数据：" + oldJson);

        // 3. 等待逻辑过期
        Thread.sleep(6000);

        // 4. 查询过期数据
        Shop shop = shopService.queryWithLogicalExpire(1L);
        System.out.println("已过期时查询结果：" + shop);

        // 5. 给异步重建线程一点时间
        Thread.sleep(1000);

        // 6. 再读一次Redis，查看 expireTime 是否刷新
        String newJson = stringRedisTemplate.opsForValue().get(key);
        System.out.println("重建后Redis数据：" + newJson);
    }
}
