package com.hmdp.service;

import com.hmdp.dto.Result;
import com.hmdp.entity.VoucherOrder;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IVoucherOrderService extends IService<VoucherOrder> {


    /**
     * 根据传入的优惠券id，完成对该优惠券的秒杀下单
     * @param voucherId 秒杀的优惠券id
     * @return 返回秒杀后的订单id
     */
    Result seckillVoucher(Long voucherId);

    /**
     * 验证用户是否第一次下单，是则完成下单，否则返回错误信息，保证一人只能下一单
     * @param voucherId 优惠券id
     * @return 秒杀结果
     */
    Result checkAndOder(Long voucherId);
}
