package com.example.seckill.service;

import com.example.seckill.common.response.R;

/**
 * 秒杀服务接口
 */
public interface SeckillService {

    /**
     * 执行秒杀下单
     * @param userId 用户ID
     * @param goodsId 商品ID
     * @return 响应结果
     */
    R doSeckill(Long userId, Long goodsId);
}