package com.example.seckill.controller;

import com.example.seckill.common.response.R;
import com.example.seckill.dto.PayCallbackReq;
import com.example.seckill.service.PayCallbackService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;

/**
 * 支付回调控制器
 */
@RestController
@RequestMapping("/payment")
public class PaymentController {

    private final PayCallbackService payCallbackService;

    public PaymentController(PayCallbackService payCallbackService) {
        this.payCallbackService = payCallbackService;
    }

    /**
     * 支付回调接口
     * @param req 支付回调请求
     * @return 响应结果
     */
    @PostMapping("/callback")
    public R payCallback(@Valid @RequestBody PayCallbackReq req) {
        return payCallbackService.payCallback(req);
    }
}