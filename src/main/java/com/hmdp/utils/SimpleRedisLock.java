package com.hmdp.utils;

import cn.hutool.core.util.BooleanUtil;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;

/**
 * @author: lsq637
 * @since: 2022-11-28 11:23:08
 * @describe:
 */
public class SimpleRedisLock implements ILock {
    /**
     * 锁的名称
    * */
    private String name;
    private StringRedisTemplate redisTemplate;
    private final String KEY_PREFIX = "lock:";

    public SimpleRedisLock(String name, StringRedisTemplate redisTemplate) {

        this.name = name;
        this.redisTemplate = redisTemplate;
    }

    /**
     * @param timoutSec 锁的过期时间，防止因服务宕机而导致锁不释放
     * @return 是否获取锁成功
     */
    @Override
    public boolean tryLock(Long timoutSec) {

        long threadId = Thread.currentThread().getId();
        Boolean success = redisTemplate.opsForValue().
                setIfAbsent(KEY_PREFIX + name, threadId + "", Duration.ofSeconds(timoutSec));
        return BooleanUtil.isTrue(success);
    }

    /**
    *释放锁
    */
    @Override
    public void unlock() {
        redisTemplate.delete(KEY_PREFIX + name);
    }
}
