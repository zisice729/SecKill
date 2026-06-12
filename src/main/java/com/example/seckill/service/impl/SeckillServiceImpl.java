package com.example.seckill.service.impl;

import com.example.seckill.common.exception.BusinessException;
import com.example.seckill.common.util.SnowflakeIdGenerator;
import com.example.seckill.controller.request.SeckillRequest;
import com.example.seckill.controller.response.SeckillResponse;
import com.example.seckill.mq.msg.SeckillOrderMsg;
import com.example.seckill.mq.producer.KafkaProducer;
import com.example.seckill.repository.RedisRepository;
import com.example.seckill.repository.entity.SeckillGoods;
import com.example.seckill.repository.mapper.SeckillGoodsMapper;
import com.example.seckill.service.SeckillService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import javax.annotation.Resource;
import java.math.BigDecimal;

@Slf4j
@Service
public class SeckillServiceImpl implements SeckillService {

    @Resource
    private RedisRepository redisRepository;

    @Resource
    private SeckillGoodsMapper goodsMapper;

    @Resource
    private KafkaProducer kafkaProducer;

    @Resource
    private SnowflakeIdGenerator snowflakeIdGenerator;

    @Override
    public SeckillResponse doSeckill(SeckillRequest request) {
        SeckillGoods goods = goodsMapper.selectByPrimaryKey(request.getGoodsId());
        if (ObjectUtils.isEmpty(goods)) {
            throw new BusinessException("商品不存在");
        }

        boolean success = redisRepository.doSeckill(request.getGoodsId(), request.getUserId());
        if (!success) {
            throw new BusinessException("库存不足或已抢购");
        }

        String orderId = String.valueOf(snowflakeIdGenerator.nextId());
        String traceId = String.valueOf(snowflakeIdGenerator.nextId());

        sendSeckillOrderMsg(orderId, traceId, request.getGoodsId(), request.getUserId(), goods.getSeckillPrice());

        SeckillResponse response = new SeckillResponse();
        response.setOrderId(orderId);
        response.setTraceId(traceId);
        return response;
    }

    /**
     * 发送秒杀订单MQ消息
     */
    private void sendSeckillOrderMsg(String orderId, String traceId, Long goodsId, Long userId, BigDecimal seckillPrice) {
        SeckillOrderMsg msg = new SeckillOrderMsg();
        msg.setOrderId(orderId);
        msg.setGoodsId(goodsId);
        msg.setUserId(userId);
        msg.setSeckillPrice(seckillPrice);
        msg.setQuantity(1);
        msg.setCreateTime(System.currentTimeMillis());
        msg.setTraceId(traceId);
        kafkaProducer.sendOrderMsg(msg);
    }
}