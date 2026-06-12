package com.example.seckill.mq.msg;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class SeckillOrderMsg {

    private String orderId;
    private Long goodsId;
    private Long userId;
    private BigDecimal seckillPrice;
    private Integer quantity;
    private Long createTime;
    private String traceId;
}