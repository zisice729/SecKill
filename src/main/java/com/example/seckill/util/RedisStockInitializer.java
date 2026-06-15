package com.example.seckill.util;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;

/**
 * Redis库存初始化工具类
 */
@Component
public class RedisStockInitializer {

    private final StringRedisTemplate redisTemplate;

    public RedisStockInitializer(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 初始化Redis库存（应用启动时执行）
     */
    @PostConstruct
    public void initStock() {
        // 初始化商品库存
        redisTemplate.opsForValue().set("seckill:stock:1", "100");
        redisTemplate.opsForValue().set("seckill:stock:2", "50");
        redisTemplate.opsForValue().set("seckill:stock:3", "200");

        // 清空用户限购集合
        redisTemplate.delete("seckill:user:1");
        redisTemplate.delete("seckill:user:2");
        redisTemplate.delete("seckill:user:3");

        System.out.println("Redis库存初始化完成");
    }
}