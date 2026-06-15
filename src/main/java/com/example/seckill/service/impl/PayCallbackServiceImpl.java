package com.example.seckill.service.impl;

import com.example.seckill.common.response.R;
import com.example.seckill.constant.OrderStatus;
import com.example.seckill.dto.PayCallbackReq;
import com.example.seckill.entity.Order;
import com.example.seckill.mapper.OrderMapper;
import com.example.seckill.service.PayCallbackService;
import com.example.seckill.util.RetryUtil;
import org.springframework.stereotype.Service;

/**
 * 支付回调服务实现类
 */
@Service
public class PayCallbackServiceImpl implements PayCallbackService {

    private final OrderMapper orderMapper;
    private final RetryUtil retryUtil;

    // 支付查询：最多3次重试，间隔 1s、2s、4s
    private static final int PAY_MAX_RETRY = 3;
    private static final long[] PAY_DELAYS = {1000, 2000, 4000};

    public PayCallbackServiceImpl(OrderMapper orderMapper, RetryUtil retryUtil) {
        this.orderMapper = orderMapper;
        this.retryUtil = retryUtil;
    }

    @Override
    public R payCallback(PayCallbackReq req) {
        String orderId = req.getOrderId();
        String outPayNo = req.getOutPayNo();

        // 重试查询订单
        Order order = retryUtil.retry(PAY_MAX_RETRY, PAY_DELAYS,
                () -> orderMapper.selectByOrderId(orderId));

        if (order == null) {
            return R.fail("订单不存在，执行退款");
        }

        // 乐观锁更新支付状态
        int rows = orderMapper.payOrder(orderId, outPayNo, OrderStatus.UN_PAY);
        if (rows > 0) {
            return R.success("支付处理成功");
        } else {
            return R.fail("订单状态已变更");
        }
    }
}