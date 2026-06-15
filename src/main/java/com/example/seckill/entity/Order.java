package com.example.seckill.entity;

import com.example.seckill.constant.OrderStatus;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 订单实体类
 */
@Data
public class Order {
    /**
     * 订单号（消费端生成）
     */
    private String orderId;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 商品ID
     */
    private Long goodsId;

    /**
     * 订单状态：UN_PAY未支付 / PAYED已支付 / CANCELED已取消
     */
    private String status;

    /**
     * 15分钟支付截止时间
     */
    private LocalDateTime payDeadline;

    /**
     * 支付时间
     */
    private LocalDateTime payTime;

    /**
     * 关单时间
     */
    private LocalDateTime closeTime;

    /**
     * 外部支付流水号
     */
    private String outPayNo;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
}