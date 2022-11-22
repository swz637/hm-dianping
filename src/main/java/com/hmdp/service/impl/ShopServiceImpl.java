package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSON;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.RedisData;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisConstants;
import org.apache.ibatis.jdbc.Null;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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
    @Autowired
    private CacheClient cacheClient;

    @Override
    public Result queryById(Long id) {

        Shop shop = cacheClient.queryWithLogicalExpire(CACHE_SHOP_KEY, id, Shop.class,
                this::getById,Duration.ofMinutes(CACHE_SHOP_TTL) );
        if (shop == null) {
            return Result.fail("店铺不存在");
        }
        return Result.ok(shop);
    }


    /**
     * 将shop保存到redis
     *
     * @param shopId     id
     * @param expireTime 设置的逻辑过期时间
     */
    public void saveShopToRedis(Long shopId, Long expireTime) {

        Shop shop = getById(shopId);
        RedisData<Shop> shopRedisData = new RedisData<>();
        shopRedisData.setExpireTime(LocalDateTime.now().plusSeconds(expireTime));
        shopRedisData.setData(shop);

        redisTemplate.opsForValue().set(CACHE_SHOP_KEY + shopId, JSONUtil.toJsonStr(shopRedisData));

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
