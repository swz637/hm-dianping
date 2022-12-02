package com.hmdp.service.impl;

import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.IDWorker;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.concurrent.*;

import static com.hmdp.utils.RedisConstants.CACHE_SECKILL_VOUCHER_KEY;
import static com.hmdp.utils.RedisConstants.CACHE_SECKILL_VOUCHER_KEY_TTL;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
@Slf4j
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Autowired
    private ISeckillVoucherService seckillVoucherService;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private IDWorker idWorker;

    @Autowired
    private RedissonClient redissonClient;

    @Autowired
    private CacheClient cacheClient;

    @Autowired
    private static final DefaultRedisScript<Long> SCRIPT;

    static {
        SCRIPT = new DefaultRedisScript<>();
        SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
        SCRIPT.setResultType(Long.class);
    }

    private static final BlockingQueue<VoucherOrder> VOUCHER_ORDER_QUEUE = new LinkedBlockingQueue<>(1024 * 1024);
    private IVoucherOrderService proxy;

    @Override
    public Result seckillVoucher(Long voucherId) {
        //从数据库查询秒杀券的信息
        // 将查询到的优惠券放入redis，先缓存预热，再使用逻辑过期解决
        SeckillVoucher seckillVoucher = cacheClient.queryWithLogicalExpire(CACHE_SECKILL_VOUCHER_KEY, voucherId, SeckillVoucher.class,
                seckillVoucherService::getById, Duration.ofDays(CACHE_SECKILL_VOUCHER_KEY_TTL));

        //判断是否在秒杀时间段内
        if (seckillVoucher.getBeginTime().isAfter(LocalDateTime.now())) {
            //否，返回错误信息
            return Result.fail("秒杀还未开始！");
        }
        if (seckillVoucher.getEndTime().isBefore(LocalDateTime.now())) {
            return Result.fail("秒杀已经结束！");
        }

        //执行脚本，验证购买资格，库存是否充足
        Long userId = UserHolder.getUser().getId();
        Long res = redisTemplate.execute(SCRIPT,
                Collections.emptyList(),
                voucherId.toString(),
                userId.toString());
        int r = res.intValue();
        if (r != 0) {
            //返回对应的错误信息
            return Result.fail(res == 1 ? "库存不足！" : "不能重复下单！");
        }
        //若有购买资格
        //保存到消息队列
        long oderId = idWorker.getId("oder");
        VoucherOrder voucherOrder = new VoucherOrder();
        voucherOrder.setUserId(userId);
        voucherOrder.setVoucherId(voucherId);
        voucherOrder.setId(oderId);
        VOUCHER_ORDER_QUEUE.add(voucherOrder);
        //注意：在此处对代理对象赋值，不能在属性中直接赋值，否则报错
        proxy = (IVoucherOrderService) AopContext.currentProxy();
        //返回订单id
        return Result.ok(oderId);
    }

    /**
     * 单线程的线程池，用来异步将阻塞队列中的，订单同步到数据库
     */
    private static final ExecutorService EXECUTORSERVICE = Executors.newSingleThreadExecutor();

    @PostConstruct
    void init() {
        //本类初始化后，就提交任务到线程池
        EXECUTORSERVICE.submit(new VoucherOrderHandler());
    }

    private class VoucherOrderHandler implements Runnable {

        @Override
        public void run() {

            while (true) {
                try {
                    //从阻塞队列中取出订单信息
                    VoucherOrder voucherOrder = VOUCHER_ORDER_QUEUE.take();
                    //使用代理对象调用方法，保证事务生效
                    //错误示范：直接在子线程中获取代理对象->IVoucherOrderService proxy =
                    // (IVoucherOrderService) AopContext.currentProxy();
                    proxy.checkAndOder(voucherOrder);
                } catch (Exception e) {
                    log.error("从阻塞队列取出订单时出错！",e);
                }
            }
        }
    }


    /**
     * 完成下单
     *
     * @param voucherOrder 传入要写入数据库的订单对象
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public void checkAndOder(VoucherOrder voucherOrder) {
        //redis已经验证完一人一单，且保证库存充足，
        // 即到这一步的一定是有效订单，直接执行业务

        //扣减库存，使用乐观锁，在更新库存时判断库存是否大于0
        boolean success = seckillVoucherService.update().
                setSql("stock = stock - 1").
                eq("voucher_id", voucherOrder.getVoucherId()).
                gt("stock", 0).
                update();
        //创建订单
        save(voucherOrder);
        if (!success) {
            log.error("扣减数据库库存失败！");
        }
    }
}
