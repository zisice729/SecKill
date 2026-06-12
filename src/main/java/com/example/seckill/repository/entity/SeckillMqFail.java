package com.example.seckill.repository.entity;

import lombok.Data;

import java.util.Date;

@Data
public class SeckillMqFail {

    private Long id;
    private String orderId;
    private String messageBody;
    private String errorStack;
    private Date createTime;
    private Integer retryCount;
    private Integer status;
    private Date updateTime;
}