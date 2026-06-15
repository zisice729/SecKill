package com.example.seckill.constant;

/**
 * Redis Key常量
 */
public class RedisKeyConstant {
    /**
     * 秒杀商品库存Key
     * 格式: seckill:stock:{goodsId}
     */
    public static final String SECKILL_STOCK = "seckill:stock:%s";

    /**
     * 秒杀用户限购集合Key
     * 格式: seckill:user:{goodsId}
     */
    public static final String SECKILL_USER_SET = "seckill:user:%s";
}