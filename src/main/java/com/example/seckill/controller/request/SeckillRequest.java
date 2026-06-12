package com.example.seckill.controller.request;

import lombok.Data;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Positive;

@Data
public class SeckillRequest {

    @NotNull(message = "商品ID不能为空")
    @Positive(message = "商品ID必须大于0")
    private Long goodsId;

    @NotNull(message = "用户ID不能为空")
    @Positive(message = "用户ID必须大于0")
    private Long userId;
}