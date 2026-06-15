package com.example.seckill.service;

/**
 * Kafka消费者服务接口
 */
public interface KafkaConsumerService {

    /**
     * 消费Kafka消息
     * @param userId 用户ID
     * @param goodsId 商品ID
     */
    void consume(Long userId, Long goodsId);
}