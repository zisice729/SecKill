package com.example.seckill.service;

import com.example.seckill.common.response.R;
import com.example.seckill.dto.PayCallbackReq;

/**
 * 支付回调服务接口
 */
public interface PayCallbackService {

    /**
     * 支付回调处理
     * @param req 支付回调请求
     * @return 响应结果
     */
    R payCallback(PayCallbackReq req);
}