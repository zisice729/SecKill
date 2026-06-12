package com.example.seckill.service;

public interface PaymentCallbackService {

    /**
     * 处理支付回调
     * @param orderId 订单ID
     * @return 处理结果
     */
    String handlePaymentCallback(String orderId);

}