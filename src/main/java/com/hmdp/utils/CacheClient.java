package com.hmdp.utils;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.entity.RedisData;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.*;
import java.util.function.Function;

import static com.hmdp.utils.RedisConstants.*;

/**
 * @author:lsq637
 * @since: 2022-11-21 16:08:28
 * @describe: 关于redis缓存的工具类
 */
@Component
@Slf4j
public class CacheClient {

    @Autowired
    private StringRedisTemplate redisTemplate;

    /**
     * 将任意的对象转换为json字符串存入redis中
     *
     * @param key     键
     * @param value   值
     * @param timeout 字段的过期时间
     */
    public void set(String key, Object value, Duration timeout) {

        redisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value), timeout);
    }

    /**
     * 传入一个对象，将其封装成带有逻辑过期字段的一个新的对象，并存入redis
     *
     * @param key     键
     * @param value   要封装的对象
     * @param timeout 逻辑过期时间
     */
    public void setWithLogical(String key, Object value, Duration timeout) {

        RedisData<Object> redisData = new RedisData<>();
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(timeout.getSeconds()));
        redisData.setData(value);
        //log.info("将传入对象封装成->"+ redisData);

        redisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
    }

    /**
     * 存入空字符串解决缓存穿透
     *
     * @param keyPrefix  key的前缀
     * @param id         要查询的对象的id
     * @param type       要查询对象的类型
     * @param dbFallBack 查询该对象的数据库逻辑
     * @param timeout    缓存重建时要设置的逻辑过期时间
     * @param <R>        返回值泛型
     * @param <ID>       id的泛型，ID类型不确定
     * @return R
     */
    public <R, ID> R queryWithPassThrough(String keyPrefix, ID id, Class<R> type, Function<ID, R> dbFallBack, Duration timeout) {

        String key = keyPrefix + id;
        String json = redisTemplate.opsForValue().get(key);
        if (StrUtil.isNotBlank(json)) {
            //redis中查询到了有效数据
            R r = JSONUtil.toBean(json, type);
            return r;
        }
        if (json != null && json.equals("")) {
            //redis中查询到的是空串
            return null;
        }

        //redis中没有有效数据，也没有为了防止缓存穿透而存入的空字符串时，开始缓存重建、
        R r = dbFallBack.apply(id);
        if (r == null) {
            //数据库中也不存在
            redisTemplate.opsForValue().set(key, "", Duration.ofMinutes(RedisConstants.CACHE_NULL_TTL));
            return null;
        }
        //数据库中存在，将数据更新到redis
        this.set(key, r, timeout);
        return r;
    }

    //private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(5);
    private static final ExecutorService CACHE_REBUILD_EXECUTOR = new ThreadPoolExecutor(3, 5,
            2, TimeUnit.MINUTES, new LinkedBlockingDeque<>(5));

    /**
     * 使用逻辑过期解决缓存击穿，在当查询到的数据逻辑过期后，尝试获取锁后，开启新的线程完成缓存的重建，本线程返回旧的数据
     *
     * @param keyPrefix  key的前缀
     * @param id         要查询的对象的id
     * @param type       要查询对象的类型
     * @param dbFallBack 查询该对象的数据库逻辑
     * @param timeout    缓存重建时要设置的逻辑过期时间
     * @param <R>        返回值泛型
     * @param <ID>       id的泛型，ID类型不确定
     * @return R
     */
    public <R, ID> R queryWithLogicalExpire(String keyPrefix, ID id, Class<R> type, Function<ID, R> dbFallBack, Duration timeout) {

        String key = keyPrefix + id;
        //从redis中获取数据
        String redisDataStr = redisTemplate.opsForValue().get(key);
        RedisData redisData = JSONUtil.toBean(redisDataStr, RedisData.class);
        LocalDateTime expireTime = redisData.getExpireTime();
        JSONObject data = (JSONObject) redisData.getData();
        R r = data.toBean(type);
        if (expireTime.isAfter(LocalDateTime.now())) {
            //未过期
            return r;
        }
        //已过期
        //尝试获取锁
        String lockKey = LOCK_SHOP_KEY + id;
        boolean haseLock = tryLock(lockKey);
        if (!haseLock) {
            //获取锁失败，说明已经有线程在执行重建缓存的操作，直接返回旧的数据
            return r;
        }
        //获取锁成功，开始重建缓存
        //开启线程
        CACHE_REBUILD_EXECUTOR.submit(() -> {
            try {
                //重建缓存
                R r1 = dbFallBack.apply(id);
                this.setWithLogical(key, r, timeout);
            } catch (Exception e) {
                throw new RuntimeException(e);
            } finally {
                unLock(lockKey);
            }
        });
        //本线程也返回过期的数据
        return r;
    }

    /**
     * 使用redis的setnx命令实现互斥锁
     *
     * @param key 锁的名称
     * @return 获取锁成功时返回true
     */
    public boolean tryLock(String key) {

        Boolean aBoolean = redisTemplate.opsForValue().setIfAbsent(key, "1", Duration.ofSeconds(LOCK_SHOP_TTL));
        return BooleanUtil.isTrue(aBoolean);
    }

    /**
     * 释放锁
     *
     * @param key 锁的名称
     */
    public void unLock(String key) {

        redisTemplate.delete(key);
    }


}
