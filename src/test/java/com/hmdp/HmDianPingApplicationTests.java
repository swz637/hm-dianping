package com.hmdp;

import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.CreditCodeUtil;
import com.hmdp.service.IShopService;
import com.hmdp.service.impl.SeckillVoucherServiceImpl;
import com.hmdp.service.impl.ShopServiceImpl;
import com.hmdp.utils.IDWorker;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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

        seckillVoucherService.saveVouchers(2L, Duration.ofDays(1L));

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

}
