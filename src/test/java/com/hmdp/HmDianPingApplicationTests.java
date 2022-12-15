package com.hmdp;

import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.CreditCodeUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.entity.Blog;
import com.hmdp.entity.Shop;
import com.hmdp.service.IShopService;
import com.hmdp.service.impl.SeckillVoucherServiceImpl;
import com.hmdp.service.impl.ShopServiceImpl;
import com.hmdp.utils.IDWorker;
import com.hmdp.utils.RedisConstants;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

import static com.hmdp.utils.RedisConstants.LOGIN_CODE_KEY;

@SpringBootTest
class HmDianPingApplicationTests {

    @Autowired
    StringRedisTemplate redisTemplate;

    @Autowired
    ShopServiceImpl shopService;

    @Autowired
    SeckillVoucherServiceImpl seckillVoucherService;

    public void setUp() throws Exception {

        System.out.println("dsaf");

    }

    /**
     * 预热redis添加店铺信息
     */
    @Test
    public void testSaveShop() {

        shopService.saveShopToRedis(2L, 10L);
        shopService.saveShopToRedis(3L, 10L);
        shopService.saveShopToRedis(4L, 10L);
    }

    /**
     * 预热redis，添加秒杀券的信息
     */
    @Test
    public void testSaveSeckillVouchers() {

        seckillVoucherService.saveVouchers(18L, Duration.ofDays(1L));

    }

    @Test
    public void getTimeStamp() {

        System.out.println(LocalDateTime.of(1998, 10, 4, 0, 0).toEpochSecond(ZoneOffset.UTC));
    }

    @Autowired
    private IDWorker idWorker;

    private final ExecutorService executor = Executors.newFixedThreadPool(500);

    @Test
    public void testIDworker() throws InterruptedException {

        //门栓计数器
        CountDownLatch latch = new CountDownLatch(300);
        //定义任务，每个线程都生成100个id
        Runnable task = () -> {
            for (int i = 0; i < 100; i++) {
                long oderId = idWorker.getId("oder");
                System.out.println(oderId);
            }
            //每个线程完成任务后就减一次计数器
            latch.countDown();
        };
        //记录开始线程的时间
        long start = System.currentTimeMillis();
        //开启300个线程
        for (int i = 0; i < 300; i++) {
            executor.submit(task);
        }
        //主线程在此等待，等到所有的子线程完成，再统计耗时
        latch.await();
        long end = System.currentTimeMillis();
        System.out.println("总耗时：" + (end - start));
    }

    @Test
    public void testBlog() {

        Blog blog = new Blog();
        Blog blog1 = blog.setId(1L);
    }

    /**
     * 将数据库的店铺按类型分类导入到redis的geo数据类型中
     */
   /* @Test
    public void saveShopTypeToRedis() {
        //查询出所有的店铺信息
        List<Shop> shops = shopService.list();
        //将店铺按类型id分组
        Map<Long, List<Shop>> listMap = shops.stream().collect(Collectors.groupingBy(Shop::getTypeId));

        //遍历分组后的map
        for (Long shopTypeId : listMap.keySet()) {
            //拿到本组的key
            String key = RedisConstants.SHOP_GEO_KEY + shopTypeId;
            //拿到本组对应的商铺列表
            List<Shop> shopList = listMap.get(shopTypeId);
            //将本组查到的店铺封装成GeoLocation对象，并放入集合，方便批量添加到redis
            List<RedisGeoCommands.GeoLocation<String>> geoLocations = new ArrayList<>(shopList.size());
            for (Shop shop : shopList) {
                geoLocations.add(
                        new RedisGeoCommands.GeoLocation<>(
                                shop.getId().toString(),
                                new Point(shop.getX(), shop.getY())
                        )
                );
            }
            //批量添加到redis
            redisTemplate.opsForGeo().add(key, geoLocations);
        }

    }*/

    /**
     * 分批导入
     */
    @Test
    public void saveShopTypeToRedis() {
        //查询出所有的店铺信息
        //List<Shop> shops = shopService.list();
        List<Shop> shops;
        int page = 0;
        int pageSize = 5;
        int currentPage = 1;
        // 2022/12/11 断点打到 while (true) {可不可行? 答：不可行！！
        while (true) {
            //shops = shopService.query().last("limit " + page * pageSize + " ," + pageSize).list();
            Page<Shop> pageLis = shopService.query().page(new Page<>(currentPage, 5));
            List<Shop> records = pageLis.getRecords();
            /*if (shops == null || shops.isEmpty()){
                break;
            }*/
            if (records == null || records.isEmpty()){
                break;
            }
            //page++;
            currentPage ++;
            //将店铺按类型id分组
            Map<Long, List<Shop>> listMap = records.stream().collect(Collectors.groupingBy(Shop::getTypeId));

            //遍历分组后的map
            for (Long shopTypeId : listMap.keySet()) {
                //拿到本组的key
                String key = RedisConstants.SHOP_GEO_KEY + shopTypeId;
                //拿到本组对应的商铺列表
                List<Shop> shopList = listMap.get(shopTypeId);
                //将本组查到的店铺封装成GeoLocation对象，并放入集合，方便批量添加到redis
                List<RedisGeoCommands.GeoLocation<String>> geoLocations = new ArrayList<>(shopList.size());
                for (Shop shop : shopList) {
                    geoLocations.add(
                            new RedisGeoCommands.GeoLocation<>(
                                    shop.getId().toString(),
                                    new Point(shop.getX(), shop.getY())
                            )
                    );
                }
                //批量添加到redis
                redisTemplate.opsForGeo().add(key, geoLocations);
            }
        }


    }

}
