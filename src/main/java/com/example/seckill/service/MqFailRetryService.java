package com.example.seckill.service;

public interface MqFailRetryService {

    /**
     * 重试MQ消费失败的订单
     */
    void retryFailedOrders();

}