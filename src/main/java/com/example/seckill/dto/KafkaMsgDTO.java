package com.example.seckill.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * Kafka消息体（无orderId）
 */
@Data
public class KafkaMsgDTO implements Serializable {
    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 商品ID
     */
    private Long goodsId;
}