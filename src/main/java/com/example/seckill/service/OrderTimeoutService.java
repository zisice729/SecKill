package com.example.seckill.service;

import com.example.seckill.repository.entity.SeckillOrder;

import java.util.List;

public interface OrderTimeoutService {

    /**
     * 处理超时未支付订单
     */
    void processTimeoutOrders();

    /**
     * 关闭单个订单
     */
    boolean closeOrder(SeckillOrder order);

}