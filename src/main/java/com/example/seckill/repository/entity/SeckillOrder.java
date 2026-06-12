package com.example.seckill.repository.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.util.Date;

@Data
public class SeckillOrder {

    private Long id;
    private String orderId;
    private Long goodsId;
    private Long userId;
    private BigDecimal price;
    private Integer quantity;
    private BigDecimal totalAmount;
    private Integer status;
    private Date payTimeout;
    private Date closeTime;
    private Date createTime;
    private Date updateTime;
}