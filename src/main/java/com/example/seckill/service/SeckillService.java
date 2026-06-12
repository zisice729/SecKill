package com.example.seckill.service;

import com.example.seckill.controller.request.SeckillRequest;
import com.example.seckill.controller.response.SeckillResponse;

public interface SeckillService {

    SeckillResponse doSeckill(SeckillRequest request);
}