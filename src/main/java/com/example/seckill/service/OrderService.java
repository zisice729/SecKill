package com.example.seckill.service;

import com.example.seckill.mq.msg.SeckillOrderMsg;

public interface OrderService {

    void createOrder(SeckillOrderMsg msg);

    boolean isOrderExists(String orderId);

}