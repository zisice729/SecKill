package com.example.seckill.service.impl;

import com.example.seckill.mq.msg.SeckillOrderMsg;
import com.example.seckill.repository.entity.SeckillOrder;
import com.example.seckill.repository.mapper.SeckillGoodsMapper;
import com.example.seckill.repository.mapper.SeckillOrderMapper;
import com.example.seckill.service.OrderService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.ObjectUtils;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;

@Slf4j
@Service
public class OrderServiceImpl implements OrderService {

    @Resource
    private SeckillOrderMapper orderMapper;

    @Resource
    private SeckillGoodsMapper goodsMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void createOrder(SeckillOrderMsg msg) {
        log.info("Creating order: {}", msg.getOrderId());

        // 幂等性校验：检查订单是否已存在
        SeckillOrder existingOrder = orderMapper.selectByOrderId(msg.getOrderId());
        if (!ObjectUtils.isEmpty(existingOrder)) {
            log.info("Order {} already exists, skip creation", msg.getOrderId());
            return;
        }

        // 计算支付超时时间（15分钟后）
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime timeoutTime = now.plusMinutes(15);
        // 需要转Date存入数据库
        Date payTimeout = Date.from(timeoutTime.atZone(ZoneId.systemDefault()).toInstant());

        SeckillOrder order = new SeckillOrder();
        order.setOrderId(msg.getOrderId());
        order.setGoodsId(msg.getGoodsId());
        order.setUserId(msg.getUserId());
        order.setPrice(msg.getSeckillPrice());
        order.setQuantity(msg.getQuantity());
        order.setTotalAmount(msg.getSeckillPrice().multiply(BigDecimal.valueOf(msg.getQuantity())));
        order.setStatus(0); // 0-未支付
        order.setPayTimeout(payTimeout);
        order.setCreateTime(new Date());
        order.setUpdateTime(new Date());

        orderMapper.insert(order);

        // 扣减MySQL真实商品库存（带乐观锁）
        int rows = goodsMapper.decreaseStockWithVersion(msg.getGoodsId(), msg.getQuantity());
        if (rows == 0) {
            throw new RuntimeException("库存不足或商品不存在");
        }

        log.info("Order created successfully: {}", msg.getOrderId());
    }

    @Override
    public boolean isOrderExists(String orderId) {
        SeckillOrder order = orderMapper.selectByOrderId(orderId);
        return !ObjectUtils.isEmpty(order);
    }
}