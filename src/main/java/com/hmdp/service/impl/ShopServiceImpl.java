package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSON;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.apache.ibatis.jdbc.Null;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;

import static com.hmdp.utils.RedisConstants.*;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Override
    public Result queryById(Long id) {

        Shop shop = queryWithMutex(id);
        if (shop == null) {
            return Result.fail("店铺不存在");
        }
        return Result.ok(shop);
    }

    /**
     * @param id
     * @return 使用互斥锁的方式解决缓存击穿问题
     * 此处互斥锁使用redis的setnx保证
     */
    public Shop queryWithMutex(Long id) {

        String key = CACHE_SHOP_KEY + id;
        //1.根据id在redis查询shop
        String jsonShop = redisTemplate.opsForValue().get(key);
        if (StrUtil.isNotBlank(jsonShop)) {
            //2.如果返回的结果不是null，且不是空字符串，为有效数据时，直接返回
            Shop shop = JSONUtil.toBean(jsonShop, Shop.class);
            return shop;
        }
        if (jsonShop != null && jsonShop.equals("")) {
            //如果结果为刚才为防止缓存穿透而设置的空串时，直接返回null
            return null;
        }

        //重建缓存
        String lockKey = LOCK_SHOP_KEY + id;
        //尝试获取锁
        boolean lock = tryLock(lockKey);
        Shop shop = null;
        try {
            if (!lock) {
                //失败，休眠一段时间后重试
                Thread.sleep(50);
                return queryWithMutex(id);
            }
            //成功，再次尝试从redis中获取数据
            jsonShop = redisTemplate.opsForValue().get(key);
            if (StrUtil.isNotBlank(jsonShop)) {
                //redis有则直接返回
                //2.如果返回的结果不是null，且不是空字符串与“\n\t”，为有效数据时，直接返回
                shop = JSONUtil.toBean(jsonShop, Shop.class);
                return shop;
            }
            if (jsonShop != null && jsonShop.equals("")) {
                //如果结果为刚才为防止缓存穿透而设置的空串时，直接返回null
                return null;
            }
            //redis没有则从数据库查询
            Thread.sleep(300);
            shop = getById(id);
            if (shop == null) {
                //数据库不存在
                //将空字符串写入redis中,因此第二次访问时会直接从缓存里面取到空串
                redisTemplate.opsForValue().set(key, "",
                        Duration.ofMinutes(CACHE_NULL_TTL));
                return null;
            }
            //存在，将查到的信息放入redis，并返回
            redisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop),
                    Duration.ofMinutes(CACHE_SHOP_TTL));
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            unLock(lockKey);
        }
        return shop;
    }
    
    public boolean tryLock(String key) {

        Boolean aBoolean = redisTemplate.opsForValue().setIfAbsent(key, "1", Duration.ofSeconds(LOCK_SHOP_TTL));
        return BooleanUtil.isTrue(aBoolean);
    }

    public void unLock(String key) {

        redisTemplate.delete(key);
    }

    @Override
    @Transactional
    public Result updateShop(Shop shop) {

        Long id = shop.getId();
        if (id == null) {
            return Result.fail("数据错误！更新失败");
        }
        //更新数据库
        updateById(shop);
        //删除缓存
        redisTemplate.delete(CACHE_SHOP_KEY + id);

        return Result.ok("更新成功！");
    }
}
