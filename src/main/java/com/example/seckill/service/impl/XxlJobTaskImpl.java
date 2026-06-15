package com.example.seckill.service.impl;

import com.example.seckill.constant.OrderStatus;
import com.example.seckill.constant.RedisKeyConstant;
import com.example.seckill.entity.Order;
import com.example.seckill.entity.SeckillMqFailLog;
import com.example.seckill.mapper.GoodsMapper;
import com.example.seckill.mapper.OrderMapper;
import com.example.seckill.mapper.SeckillMqFailLogMapper;
import com.example.seckill.service.XxlJobTaskService;
import com.example.seckill.util.IdUtil;
import com.example.seckill.util.RetryUtil;
import com.xxl.job.core.handler.annotation.XxlJob;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * XXL-Job定时任务实现类
 */
@Service
public class XxlJobTaskImpl implements XxlJobTaskService {

    private final SeckillMqFailLogMapper failLogMapper;
    private final OrderMapper orderMapper;
    private final GoodsMapper goodsMapper;
    private final StringRedisTemplate redisTemplate;
    private final RetryUtil retryUtil;
    private final IdUtil idUtil;

    // 异常记录最大自动重试次数
    private static final int TASK_MAX_RETRY = 5;

    public XxlJobTaskImpl(SeckillMqFailLogMapper failLogMapper,
                          OrderMapper orderMapper,
                          GoodsMapper goodsMapper,
                          StringRedisTemplate redisTemplate,
                          RetryUtil retryUtil,
                          IdUtil idUtil) {
        this.failLogMapper = failLogMapper;
        this.orderMapper = orderMapper;
        this.goodsMapper = goodsMapper;
        this.redisTemplate = redisTemplate;
        this.retryUtil = retryUtil;
        this.idUtil = idUtil;
    }

    /**
     * XXL-Job 任务1：Kafka异常记录补偿重试
     * 执行频率：每分钟一次（在XXL-Job管理后台配置Cron）
     */
    @XxlJob("mqFailRetryJobHandler")
    @Override
    public void mqFailRetryJob() {
        List<SeckillMqFailLog> failList = failLogMapper.selectUnHandleLog(TASK_MAX_RETRY);
        for (SeckillMqFailLog log : failList) {
            Long userId = log.getUserId();
            Long goodsId = log.getGoodsId();

            try {
                // 幂等判断
                Order existOrder = orderMapper.selectByUserAndGoods(userId, goodsId);
                if (existOrder != null) {
                    failLogMapper.markAsProcessed(log.getId());
                    continue;
                }

                // 重试执行建单+扣库存
                Boolean success = retryUtil.retry(1, new long[]{1000},
                        () -> createOrderAndStock(userId, goodsId));

                if (Boolean.TRUE.equals(success)) {
                    failLogMapper.markAsProcessed(log.getId());
                } else {
                    // 更新重试次数
                    int newRetryCount = log.getRetryCount() + 1;
                    failLogMapper.updateRetryCount(log.getId(), newRetryCount);

                    // 达到最大重试次数，标记为人工处理
                    if (newRetryCount >= TASK_MAX_RETRY) {
                        failLogMapper.markAsManual(log.getId());
                    }
                }
            } catch (Exception e) {
                // 异常情况更新重试次数
                int newRetryCount = log.getRetryCount() + 1;
                failLogMapper.updateRetryCount(log.getId(), newRetryCount);

                if (newRetryCount >= TASK_MAX_RETRY) {
                    failLogMapper.markAsManual(log.getId());
                }
            }
        }
    }

    /**
     * XXL-Job 任务2：超时关单任务
     * 执行频率：每分钟一次（在XXL-Job管理后台配置Cron）
     */
    @XxlJob("timeoutCloseOrderJobHandler")
    @Override
    public void timeoutCloseOrderJob() {
        List<Order> timeoutOrders = orderMapper.selectTimeoutOrders();
        for (Order order : timeoutOrders) {
            try {
                boolean success = closeOrderAndRestoreStock(order.getOrderId(), order.getGoodsId(), order.getUserId());
                if (!success) {
                    System.err.println("关单失败: " + order.getOrderId());
                }
            } catch (Exception e) {
                System.err.println("关单失败: " + order.getOrderId() + ", error: " + e.getMessage());
            }
        }
    }

    /**
     * 关单并恢复库存（事务保证一致性）
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean closeOrderAndRestoreStock(String orderId, Long goodsId, Long userId) {
        // 1. 乐观锁关闭订单
        int rows = orderMapper.closeOrder(orderId, OrderStatus.UN_PAY);
        if (rows == 0) {
            return false;
        }

        // 2. 恢复数据库真实库存
        goodsMapper.restoreStock(goodsId);

        // 3. 恢复Redis缓存库存
        String stockKey = String.format(RedisKeyConstant.SECKILL_STOCK, goodsId);
        String userSetKey = String.format(RedisKeyConstant.SECKILL_USER_SET, goodsId);
        redisTemplate.opsForValue().increment(stockKey);
        redisTemplate.opsForSet().remove(userSetKey, userId.toString());

        return true;
    }

    /**
     * 事务：创建订单 + 扣真实库存
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean createOrderAndStock(Long userId, Long goodsId) {
        String orderId = idUtil.nextOrderId();
        Order order = new Order();
        order.setOrderId(orderId);
        order.setUserId(userId);
        order.setGoodsId(goodsId);
        order.setStatus(OrderStatus.UN_PAY);
        order.setPayDeadline(java.time.LocalDateTime.now().plusMinutes(15));
        orderMapper.insert(order);

        int rows = goodsMapper.deductStock(goodsId);
        if (rows <= 0) {
            throw new RuntimeException("真实库存不足");
        }
        return true;
    }
}