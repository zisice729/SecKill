package com.example.seckill.service.impl;

import com.example.seckill.common.exception.BusinessException;
import com.example.seckill.repository.entity.SeckillOrder;
import com.example.seckill.repository.mapper.SeckillOrderMapper;
import com.example.seckill.service.PaymentCallbackService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.ObjectUtils;

import javax.annotation.Resource;

@Slf4j
@Service
public class PaymentCallbackServiceImpl implements PaymentCallbackService {

    @Resource
    private SeckillOrderMapper orderMapper;

    /**
     * 处理支付回调（极简，零兜底）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public String handlePaymentCallback(String orderId) {
        log.info("Processing payment callback for order: {}", orderId);

        // 根据orderId查询订单
        SeckillOrder order = orderMapper.selectByOrderId(orderId);

        // 场景3：订单不存在
        if (ObjectUtils.isEmpty(order)) {
            log.warn("Order {} not found, initiating refund", orderId);
            // 调用支付接口原路退款
            refundPayment(orderId);
            return "ORDER_NOT_FOUND";
        }

        Integer currentStatus = order.getStatus();

        // 场景2：订单存在但状态≠未支付（已取消/已支付）
        if (!ObjectUtils.isEmpty(currentStatus) && !currentStatus.equals(0)) {
            log.info("Order {} current status is {}, no action needed", orderId, currentStatus);
            if (currentStatus.equals(1)) {
                return "ALREADY_PAID";
            } else if (currentStatus.equals(2)) {
                return "ORDER_CANCELLED";
            }
            return "UNKNOWN_STATUS";
        }

        // 场景1：订单存在且状态=未支付，执行乐观锁更新
        int rows = orderMapper.updateStatus(orderId, 1); // 1-已支付

        if (rows == 0) {
            // 乐观锁更新失败，说明订单状态已被修改（被关单任务取消）
            log.warn("Order {} status changed during payment callback, possible race condition", orderId);
            // 需要原路退款
            refundPayment(orderId);
            return "STATUS_CHANGED";
        }

        log.info("Order {} payment status updated successfully", orderId);
        return "SUCCESS";

        // 注意：支付成功后无需操作Redis，因为库存已在MQ消费时完成流转
    }

    /**
     * 原路退款（简化实现）
     */
    private void refundPayment(String orderId) {
        log.info("Initiating refund for order: {}", orderId);

        // 这里调用支付平台的退款接口
        // 实际项目中需要集成支付宝、微信支付等退款API

        // 简化实现：仅记录日志
        log.error("REFUND_NEEDED: Order {} requires refund due to payment callback failure", orderId);

        // 实际代码示例：
        // AlipayClient alipayClient = ...;
        // AlipayTradeRefundRequest request = new AlipayTradeRefundRequest();
        // request.setBizContent("{" +
        //     "\"out_trade_no\":\"" + orderId + "\"," +
        //     "\"refund_amount\":\"" + amount + "\"," +
        //     "\"refund_reason\":\"订单不存在或状态异常\"" +
        //     "}");
        // AlipayTradeRefundResponse response = alipayClient.execute(request);
    }
}