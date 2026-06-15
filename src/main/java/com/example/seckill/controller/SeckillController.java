package com.example.seckill.controller;

import com.example.seckill.common.response.R;
import com.example.seckill.dto.SeckillReq;
import com.example.seckill.service.SeckillService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;

/**
 * 秒杀控制器
 */
@RestController
@RequestMapping("/seckill")
public class SeckillController {

    private final SeckillService seckillService;

    public SeckillController(SeckillService seckillService) {
        this.seckillService = seckillService;
    }

    /**
     * 秒杀下单接口
     * @param req 秒杀请求
     * @return 响应结果
     */
    @PostMapping("/do")
    public R seckill(@Valid @RequestBody SeckillReq req) {
        return seckillService.doSeckill(req.getUserId(), req.getGoodsId());
    }
}