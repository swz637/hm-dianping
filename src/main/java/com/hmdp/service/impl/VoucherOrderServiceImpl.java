package com.hmdp.service.impl;

import com.baomidou.mybatisplus.extension.conditions.query.QueryChainWrapper;
import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IVoucherService;
import com.hmdp.utils.IDWorker;
import com.hmdp.utils.UserHolder;
import org.springframework.aop.framework.AopContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Autowired
    private ISeckillVoucherService seckillVoucherService;

    @Autowired
    private IDWorker idWorker;
    @Override
    public Result seckilVoucher(Long voucherId) {

        //查询秒杀券的信息
        SeckillVoucher seckillVoucher = seckillVoucherService.getById(voucherId);
        //判断是否在秒杀时间段内
        if (seckillVoucher.getBeginTime().isAfter(LocalDateTime.now())) {
            //否，返回错误信息
            return Result.fail("秒杀还未开始！");
        }
        if (seckillVoucher.getEndTime().isBefore(LocalDateTime.now())) {
            return Result.fail("秒杀已经结束！");
        }
        if (seckillVoucher.getStock() < 1) {
            return Result.fail("库存不足！");
        }
        Long userId = UserHolder.getUser().getId();
        synchronized (userId.toString().intern()){
            IVoucherOrderService iVoucherService = (IVoucherOrderService) AopContext.currentProxy();
            return iVoucherService.getResult(voucherId);
        }
    }

    @Transactional
    @Override
    public Result getResult(Long voucherId) {

        Long userId = UserHolder.getUser().getId();
        //判断用户是否已经抢购过该优惠券
        Integer count = query().eq("user_id", userId).eq("voucher_id", voucherId).count();
        if (count != 0){
            //已经抢购过
            return Result.fail("抢购失败！，每人只能抢购一份！");
        }

        //扣减库存，使用乐观锁，在更新库存时判断库存是否大于0
        boolean success = seckillVoucherService.update().
                setSql("stock = stock - 1").
                eq("voucher_id", voucherId).
                gt("stock",0).
                update();
        if (!success){
            return Result.fail("库存不足!");
        }
        //创建订单
        VoucherOrder voucherOrder = new VoucherOrder();
        //设置订单id
        long orderId = idWorker.getId("order");
        voucherOrder.setId(orderId);
        //设置用户id

        voucherOrder.setUserId(userId);
        //设置优惠券id
        voucherOrder.setVoucherId(voucherId);
        //保存到数据库
        save(voucherOrder);
        //返回订单id
        return Result.ok(orderId);
    }
}
