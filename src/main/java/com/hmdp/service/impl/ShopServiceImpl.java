package com.hmdp.service.impl;

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
 *  服务实现类
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
        String key = CACHE_SHOP_KEY + id;
        //1.根据id在redis查询shop
        String jsonShop = redisTemplate.opsForValue().get(key);
        if (StrUtil.isNotBlank(jsonShop)) {
            //2.如果返回的结果不是null，且不是空字符串与“\n\t”，为有效数据时，直接返回
            Shop shop = JSONUtil.toBean(jsonShop, Shop.class);
            return Result.ok(shop);
        }
        if (jsonShop != null && jsonShop.equals("")){
            //如果结果为刚才为防止缓存穿透而设置的空串时，直接返回null
            return Result.fail("店铺不存在！");
        }
        //3.不存在，在数据库查询shop
        Shop shop = getById(id);
        if (shop == null) {
            //4.数据库不存在，返回错误信息
            //将空字符串写入redis中,因此第二次访问时会直接从缓存里面取到空串
            redisTemplate.opsForValue().set(key, "",
                    Duration.ofMinutes(CACHE_NULL_TTL));
            return Result.fail("店铺不存在！");
        }
        //5.存在，将查到的信息放入redis，并返回
        redisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop),
                Duration.ofMinutes(CACHE_SHOP_TTL));
        return Result.ok(shop);
    }

    @Override
    @Transactional
    public Result updateShop(Shop shop) {

        Long id = shop.getId();
        if ( id== null){
            return Result.fail("数据错误！更新失败");
        }
        //更新数据库
        updateById(shop);
        //删除缓存
        redisTemplate.delete(CACHE_SHOP_KEY + id);

        return Result.ok("更新成功！");
    }
}
