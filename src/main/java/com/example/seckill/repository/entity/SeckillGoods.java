package com.example.seckill.repository.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.util.Date;

@Data
public class SeckillGoods {

    private Long id;
    private Long goodsId;
    private String goodsName;
    private BigDecimal seckillPrice;
    private BigDecimal originalPrice;
    private Integer totalStock;
    private Integer remainingStock;
    private Integer status;
    private Date startTime;
    private Date endTime;
    private Date createTime;
    private Date updateTime;
}