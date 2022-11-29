package com.hmdp.utils;

import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.BooleanUtil;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;

import java.time.Duration;
import java.util.Collections;

/**
 * @author: lsq637
 * @since: 2022-11-28 11:23:08
 * @describe:
 */
public class SimpleRedisLock implements ILock {

    /**
     * 锁的名称
     */
    private final String name;
    private final StringRedisTemplate redisTemplate;
    private final String KEY_PREFIX = "lock:";
    private final String ID_PREFIX = UUID.randomUUID().toString(true) + "-";

    private static final DefaultRedisScript<Integer> SCRIPT;

    static {
        SCRIPT = new DefaultRedisScript<>();
        SCRIPT.setLocation(new ClassPathResource("threadId.lua"));
        SCRIPT.setResultType(Integer.class);
    }


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
                setIfAbsent(KEY_PREFIX + name, ID_PREFIX + threadId, Duration.ofSeconds(timoutSec));
        return BooleanUtil.isTrue(success);
    }

    /**
     * 使用lua脚本使获取锁对应的线程id与删除该锁操作为原子性的
     */
    @Override
    public void unlock() {

        String threadId = ID_PREFIX + Thread.currentThread().getId();
        redisTemplate.execute(SCRIPT, Collections.singletonList(KEY_PREFIX + name), threadId);
    }

    /**
     * 释放锁，对redis中的锁使用线程标识，防止由于redis中锁到期删除而造成的误删除其他线程的锁
     * 存在问题：由于获取锁对应的线程id与删除该锁，不是原子性操作，（由于jvm的fullGC，造成全局阻塞时）可能还没来得及删除锁，该锁就已经过期；此时再删除的锁就可能是其他线程的锁
     */
  /*  @Override
    public void unlock() {
        //当前线程对应的id
        String threadId = ID_PREFIX + Thread.currentThread().getId();
        String threadIdInRedis = redisTemplate.opsForValue().get(KEY_PREFIX + name);
        if (threadId.equals(threadIdInRedis)) {
            //如果redis中的锁是本线程对应的锁，再删除
            redisTemplate.delete(KEY_PREFIX + name);
        }
    }*/
}
