package hmdp.utils;

public class RedisConstants {
    public static final String CACHE_SHOP_KEY = "cache:shop:";
    public static final Long CACHE_SHOP_TTL = 30L;
    public static final Long CACHE_NULL_TTL = 2L;

    public static final String LOCK_SHOP_KEY = "lock:shop:";

    public static final String CACHE_SHOP_TYPE_KEY = "cache:shopType:list";

    public static final String LOGIN_CODE_KEY = "login:code:";
    public static final Long LOGIN_CODE_TTL = 2L;

    public static final String LOGIN_USER_KEY = "login:token:";
    public static final Long LOGIN_USER_TTL = 30L;

    public static final String SIGN_USER_KEY = "sign:";

    public static final String BLOG_LIKED_KEY = "blog:liked:";

    public static final String FOLLOW_KEY = "follows:";

    public static final String FEED_KEY = "feed:";
}
