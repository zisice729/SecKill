package com.example.seckill.controller;

import com.example.seckill.common.response.Result;
import com.example.seckill.controller.request.SeckillRequest;
import com.example.seckill.controller.response.SeckillResponse;
import com.example.seckill.service.SeckillService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.validation.Valid;

@Slf4j
@RestController
@RequestMapping("/api/seckill")
public class SeckillController {

    @Resource
    private SeckillService seckillService;

    @PostMapping("/order")
    public Result<SeckillResponse> seckill(@Valid @RequestBody SeckillRequest request) {
        SeckillResponse response = seckillService.doSeckill(request);
        return Result.success(response);
    }
}