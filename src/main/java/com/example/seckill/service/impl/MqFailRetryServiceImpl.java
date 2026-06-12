package com.example.seckill.service.impl;

import com.example.seckill.mq.msg.SeckillOrderMsg;
import com.example.seckill.repository.entity.SeckillMqFail;
import com.example.seckill.repository.mapper.SeckillMqFailMapper;
import com.example.seckill.service.MqFailRetryService;
import com.example.seckill.service.OrderService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.ObjectUtils;

import javax.annotation.Resource;
import java.util.List;

@Slf4j
@Service
public class MqFailRetryServiceImpl implements MqFailRetryService {

    @Resource
    private SeckillMqFailMapper mqFailMapper;

    @Resource
    private OrderService orderService;

    @Resource
    private ObjectMapper objectMapper;

    /**
     * 重试MQ消费失败的订单（定时任务调用）
     */
    @Override
    public void retryFailedOrders() {
        try {
            // 查询需要重试的失败记录（创建时间超过3分钟，重试次数<5，状态为未处理）
            List<SeckillMqFail> failRecords = mqFailMapper.selectPendingRetry(3);

            if (ObjectUtils.isEmpty(failRecords)) {
                log.info("No failed MQ messages to retry");
                return;
            }

            log.info("Found {} failed MQ messages to retry", failRecords.size());

            // 逐个重试失败的MQ消息
            for (SeckillMqFail failRecord : failRecords) {
                try {
                    retrySingleOrder(failRecord);
                } catch (Exception e) {
                    log.error("Failed to retry order {}: {}", failRecord.getOrderId(), e.getMessage(), e);
                }
            }

        } catch (Exception e) {
            log.error("Error in MQ fail retry task: {}", e.getMessage(), e);
        }
    }

    /**
     * 重试单个订单
     */
    @Transactional(rollbackFor = Exception.class)
    public void retrySingleOrder(SeckillMqFail failRecord) {
        String orderId = failRecord.getOrderId();
        Long id = failRecord.getId();
        Integer currentRetryCount = failRecord.getRetryCount();

        log.info("Retrying failed order: {}, current retry count: {}", orderId, currentRetryCount);

        try {
            // 幂等性校验：检查订单是否已存在
            if (orderService.isOrderExists(orderId)) {
                log.info("Order {} already exists during retry, mark as processed", orderId);
                mqFailMapper.updateStatus(id, 1); // 1-已处理
                return;
            }

            // 解析消息体
            SeckillOrderMsg msg = objectMapper.readValue(failRecord.getMessageBody(), SeckillOrderMsg.class);

            // 重新执行订单创建逻辑
            orderService.createOrder(msg);

            // 重试成功，更新状态为已处理
            mqFailMapper.updateStatus(id, 1);
            log.info("Order {} retry succeeded", orderId);

        } catch (Exception e) {
            // 重试失败，增加重试次数
            int newRetryCount = currentRetryCount + 1;
            mqFailMapper.updateRetryCount(id, newRetryCount);

            // 检查是否超过最大重试次数
            if (newRetryCount >= 5) {
                // 超过重试上限，标记为需要人工处理
                mqFailMapper.updateStatus(id, 2); // 2-人工处理
                log.error("Order {} retry exceeded max attempts, requires manual processing", orderId);

                // 触发告警通知运维人员
                sendAlertNotification(orderId, e.getMessage());
            } else {
                log.warn("Order {} retry failed, attempt {}/5: {}", orderId, newRetryCount, e.getMessage());
            }
        }
    }

    /**
     * 发送告警通知（简化实现）
     */
    private void sendAlertNotification(String orderId, String errorMessage) {
        // 这里可以集成钉钉、企业微信等告警通知
        log.error("ALERT: Order {} requires manual processing. Error: {}", orderId, errorMessage);
        // 实际项目中可以调用钉钉机器人API、发送邮件等
    }
}