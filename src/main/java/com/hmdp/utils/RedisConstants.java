package com.hmdp.utils;

public class RedisConstants {
    public static final String LOGIN_CODE_KEY = "login:code:";
    public static final Long LOGIN_CODE_TTL = 2L;
    public static final String LOGIN_USER_KEY = "login:token:";
    public static final Long LOGIN_USER_TTL = 12*60L;

    public static final Long CACHE_NULL_TTL = 2L;

    public static final Long CACHE_SHOP_TTL = 30L;
    public static final String CACHE_SHOP_KEY = "cache:shop:";
    /**
    *秒杀券的缓存key
    */
    public static final String CACHE_SECKILL_VOUCHER_KEY = "cache:secKillVoucher:";
    /**
    *秒杀券的缓存过期时间，1天
    */
    public static final Long CACHE_SECKILL_VOUCHER_KEY_TTL = 1L;
    public static final String LOCK_SHOP_KEY = "lock:shop:";
    public static final Long LOCK_SHOP_TTL = 10L;
    /**
    *优惠券订单的redisson的key
    */
    public static final String LOCK_ODER_KEY = "lock:order:";
    /**
     *优惠券订单的redisson的key的过期时间
     */
    public static final Long LOCK_ODER_TTL = 10L;



    public static final String SECKILL_STOCK_KEY = "seckill:stock:";
    public static final String BLOG_LIKED_KEY = "blog:liked:";
    public static final String FEED_KEY = "feed:";
    public static final String SHOP_GEO_KEY = "shop:geo:";
    public static final String USER_SIGN_KEY = "sign:";

    public static final String SHOP_TYPE_LIST_KEY = "cache:shops";
    public static final Long SHOP_TYPE_LIST_TTL = 60L;

}
