package com.example.seckill.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 商品实体类
 */
@Data
public class Goods {
    /**
     * 商品ID
     */
    private Long id;

    /**
     * 真实库存
     */
    private Integer stock;

    /**
     * 商品名称
     */
    private String name;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
}