package com.example.seckill.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * Kafka消费异常日志实体类
 */
@Data
public class SeckillMqFailLog {
    /**
     * 主键ID
     */
    private Long id;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 商品ID
     */
    private Long goodsId;

    /**
     * 原始消息
     */
    private String msgContent;

    /**
     * 异常信息
     */
    private String errorMsg;

    /**
     * 已重试次数
     */
    private Integer retryCount;

    /**
     * 处理状态：0未处理 1已处理 2人工处理
     */
    private Integer handleStatus;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
}