package com.hmdp.utils;

/**
 * @author: lsq637
 * @since: 2022-11-28 11:14:08
 * @describe:
 */
public interface ILock {

    /**尝试获取锁，并设置锁的过期时间
     * @param timoutSec 锁的过期时间，防止因服务宕机而导致锁不释放
     * @return 获取锁成功返回true
     */
    boolean tryLock(Long timoutSec);

    /**
     * 释放锁
     */
    void unlock();
}
