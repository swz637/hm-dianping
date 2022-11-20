package com.hmdp.service.impl;

import cn.hutool.json.JSON;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static com.hmdp.utils.RedisConstants.SHOP_TYPE_LIST_KEY;
import static com.hmdp.utils.RedisConstants.SHOP_TYPE_LIST_TTL;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Override
    public Result queryAllWithCash() {

        List<ShopType> shopTypeList = new ArrayList<>();
        //1.在redis缓存里面查询
        String shop;
        while ((shop = redisTemplate.opsForList().leftPop(SHOP_TYPE_LIST_KEY)) != null) {
            shopTypeList.add(JSONUtil.toBean(shop, ShopType.class));
        }
        if (shopTypeList.size() > 0) {
            //存在，返回
            return Result.ok(shopTypeList);
        }
        //不存在，到数据库查询
        shopTypeList = query().orderByAsc("sort").list();
        if (shopTypeList.size() == 0) {
            //不存在，返回错误信息
            return Result.fail("没有商铺信息！！");
        }
        //存在，保存到redis，返回
        //将list集合中的ShopType对象转换为json字符串
        ArrayList<String> stringShops = new ArrayList<>();
        for (ShopType shopType : shopTypeList) {
            stringShops.add(JSONUtil.toJsonStr(shopType));
        }
        redisTemplate.opsForList().rightPushAll(SHOP_TYPE_LIST_KEY, stringShops);
        redisTemplate.expire(SHOP_TYPE_LIST_KEY, Duration.ofMinutes(SHOP_TYPE_LIST_TTL));
        return Result.ok(shopTypeList);
    }
}
