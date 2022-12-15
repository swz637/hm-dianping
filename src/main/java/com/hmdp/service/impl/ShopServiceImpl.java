package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.entity.RedisData;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.SystemConstants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResult;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.domain.geo.GeoReference;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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

        Shop shop = cacheClient.queryWithPassThrough(CACHE_SHOP_KEY + "pass_through:", id, Shop.class,
                this::getById, Duration.ofMinutes(CACHE_SHOP_TTL));
        //Shop shop = cacheClient.queryWithLogicalExpire(CACHE_SHOP_KEY, id, Shop.class,
        //        this::getById,Duration.ofMinutes(CACHE_SHOP_TTL) );
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

    @Override
    public Result queryShopByType(Integer typeId, Integer current, Double x, Double y) {

        if (x == null || y == null) {
            //说明没有传入坐标参数，直接分页查询返回即可
            Page<Shop> page = query()
                    .eq("type_id", typeId)
                    .page(new Page<>(current, SystemConstants.DEFAULT_PAGE_SIZE));
            // 返回数据
            return Result.ok(page.getRecords());
        }
        String key = SHOP_GEO_KEY + typeId;
        int start = (current - 1) * SystemConstants.DEFAULT_PAGE_SIZE;
        int end = current * SystemConstants.DEFAULT_PAGE_SIZE;
        //在redis中查询离用户 5km 内的店铺，同时设置limit参数，限制查询的内容条数，结果就是按距离排序的
        GeoResults<RedisGeoCommands.GeoLocation<String>> geoResults = redisTemplate.opsForGeo().search(key,
                GeoReference.fromCoordinate(x, y),
                new Distance(5000),
                RedisGeoCommands.GeoRadiusCommandArgs.newGeoRadiusArgs().includeDistance().limit(end));

        if (geoResults == null) {
            return Result.ok(Collections.emptyList());
        }
        //获取结果中的内容
        List<GeoResult<RedisGeoCommands.GeoLocation<String>>> list = geoResults.getContent();

        //开始截取单页的内容
        if (start >= list.size()) {
            //当结束值大于总的集合个数时，返回空集合，即没有下一页了
            return Result.ok(Collections.emptyList());
        }
        //使用stream流跳过前start个
        List<GeoResult<RedisGeoCommands.GeoLocation<String>>> skip = list.stream()
                .skip(start).collect(Collectors.toList());

        //GeoResult包含GeoLocation（符合范围集分页的店铺的信息）、和查询结果中携带的两点间的距离
        //GeoLocation包含name（店铺id）和point（店铺坐标）
        ArrayList<Long> ids = new ArrayList<>(skip.size());
        HashMap<String, Distance> distanceMap = new HashMap<>();
        for (GeoResult<RedisGeoCommands.GeoLocation<String>> geoResult : skip) {
            String shopIdStr = geoResult.getContent().getName();
            ids.add(Long.valueOf(shopIdStr));
            distanceMap.put(shopIdStr, geoResult.getDistance());
        }
        //指定排序字段及顺序
        String joinStr = StrUtil.join(",", ids);
        List<Shop> shopList = query().in("id", ids).last("order by field (id, " + joinStr + ")").list();
        //给每个shop设置Distance属性，即在前端展示的离商店的距离
        for (Shop shop : shopList) {
            shop.setDistance(distanceMap.get(shop.getId().toString()).getValue());
        }
        return Result.ok(shopList);
    }
}
