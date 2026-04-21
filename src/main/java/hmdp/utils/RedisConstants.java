package hmdp.utils;

public class RedisConstants {

    //商铺
    public static final String CACHE_SHOP_KEY = "cache:shop:";
    public static final Long CACHE_SHOP_TTL = 30L;
    public static final Long CACHE_NULL_TTL = 2L;

    public static final String LOCK_SHOP_KEY = "lock:shop:";

    //商铺类型
    public static final String CACHE_SHOP_TYPE_KEY = "cache:shopType:list";

    //用户登录
    public static final String LOGIN_CODE_KEY = "login:code:";
    public static final Long LOGIN_CODE_TTL = 2L;

    public static final String LOGIN_USER_KEY = "login:token:";
    public static final Long LOGIN_USER_TTL = 30L;

    //签到
    public static final String SIGN_USER_KEY = "sign:";

    //博客点赞
    public static final String BLOG_LIKED_KEY = "blog:liked:";

    //关注
    public static final String FOLLOW_KEY = "follows:";

    //粉丝推送
    public static final String FEED_KEY = "feed:";

    //商铺缓存
    public static final String SHOP_GEO_KEY = "shop:geo:";

    //秒杀订单
    public static final String SECKILL_STOCK_KEY = "seckill:stock:";
    public static final String SECKILL_ORDER_KEY = "seckill:order:";
    public static final String LOCK_ORDER_KEY = "lock:order:";
    public static final String STREAM_ORDERS = "stream.orders";
    public static final String STREAM_GROUP = "g1";
    public static final String STREAM_CONSUMER = "c1";

    //uv统计
    public static final String UV_KEY = "uv:";
}
