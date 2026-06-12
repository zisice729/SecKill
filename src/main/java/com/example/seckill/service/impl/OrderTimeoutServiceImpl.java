package com.example.seckill.service.impl;

import com.example.seckill.repository.RedisRepository;
import com.example.seckill.repository.entity.SeckillOrder;
import com.example.seckill.repository.mapper.SeckillGoodsMapper;
import com.example.seckill.repository.mapper.SeckillOrderMapper;
import com.example.seckill.service.OrderTimeoutService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.ObjectUtils;

import javax.annotation.Resource;
import java.util.Date;
import java.util.List;

@Slf4j
@Service
public class OrderTimeoutServiceImpl implements OrderTimeoutService {

    @Resource
    private SeckillOrderMapper orderMapper;

    @Resource
    private SeckillGoodsMapper goodsMapper;

    @Resource
    private RedisRepository redisRepository;

    /**
     * 处理超时未支付订单（定时任务调用）
     */
    @Override
    public void processTimeoutOrders() {
        try {
            // 查询超时未支付订单
            List<SeckillOrder> timeoutOrders = orderMapper.selectTimeoutUnpaidOrders();

            if (ObjectUtils.isEmpty(timeoutOrders)) {
                log.info("No timeout orders to process");
                return;
            }

            log.info("Found {} timeout orders to process", timeoutOrders.size());

            // 逐个处理超时订单
            for (SeckillOrder order : timeoutOrders) {
                try {
                    closeOrder(order);
                } catch (Exception e) {
                    log.error("Failed to close order {}: {}", order.getOrderId(), e.getMessage(), e);
                }
            }

        } catch (Exception e) {
            log.error("Error processing timeout orders: {}", e.getMessage(), e);
        }
    }

    /**
     * 关闭单个订单（乐观锁更新）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean closeOrder(SeckillOrder order) {
        String orderId = order.getOrderId();

        log.info("Closing timeout order: {}", orderId);

        // 乐观锁更新订单状态：只有状态为未支付(0)时才能关闭
        int rows = orderMapper.closeOrder(orderId, 2); // 2-已取消

        if (rows == 0) {
            log.info("Order {} status changed, skip closing", orderId);
            return false;
        }

        // 关单成功后归还Redis缓存库存并删除用户限购记录
        redisRepository.returnStockAndRemoveUserLimit(order.getGoodsId(), order.getUserId());

        // 归还MySQL真实库存
        goodsMapper.increaseStock(order.getGoodsId(), order.getQuantity());

        log.info("Order {} closed successfully, stock returned", orderId);
        return true;
    }
}