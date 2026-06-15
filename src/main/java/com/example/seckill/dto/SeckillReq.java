package com.example.seckill.dto;

import lombok.Data;

import javax.validation.constraints.NotNull;

/**
 * 秒杀请求DTO
 */
@Data
public class SeckillReq {

    /**
     * 用户ID
     */
    @NotNull(message = "用户ID不能为空")
    private Long userId;

    /**
     * 商品ID
     */
    @NotNull(message = "商品ID不能为空")
    private Long goodsId;
}