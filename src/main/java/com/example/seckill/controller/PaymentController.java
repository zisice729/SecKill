package com.example.seckill.controller;

import com.example.seckill.common.response.Result;
import com.example.seckill.service.PaymentCallbackService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

@Slf4j
@RestController
@RequestMapping("/api/payment")
public class PaymentController {

    @Resource
    private PaymentCallbackService paymentCallbackService;

    /**
     * 支付回调接口
     */
    @PostMapping("/callback")
    public Result<String> paymentCallback(@RequestParam String orderId) {
        log.info("Received payment callback for order: {}", orderId);

        try {
            String result = paymentCallbackService.handlePaymentCallback(orderId);

            switch (result) {
                case "SUCCESS":
                    return Result.success("支付成功");
                case "ALREADY_PAID":
                    return Result.success("订单已支付");
                case "ORDER_CANCELLED":
                    return Result.success("订单已取消，已退款");
                case "ORDER_NOT_FOUND":
                    return Result.success("订单不存在，已退款");
                case "STATUS_CHANGED":
                    return Result.success("订单状态已变更，已退款");
                default:
                    return Result.error("未知状态");
            }

        } catch (Exception e) {
            log.error("Payment callback failed for order {}: {}", orderId, e.getMessage(), e);
            return Result.error("支付回调处理失败");
        }
    }
}