package com.example.seckill.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;

/**
 * 支付回调请求DTO
 */
@Data
public class PayCallbackReq {

    /**
     * 订单ID
     */
    @NotBlank(message = "订单ID不能为空")
    private String orderId;

    /**
     * 外部支付流水号
     */
    @NotBlank(message = "外部支付流水号不能为空")
    private String outPayNo;
}