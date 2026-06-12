package com.example.seckill.mq.consumer;

import com.example.seckill.common.util.ExponentialBackoffUtil;
import com.example.seckill.mq.msg.SeckillOrderMsg;
import com.example.seckill.repository.entity.SeckillMqFail;
import com.example.seckill.repository.mapper.SeckillMqFailMapper;
import com.example.seckill.service.OrderService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import javax.annotation.Resource;
import java.util.Date;

@Slf4j
@Component
public class KafkaConsumer {

    @Resource
    private OrderService orderService;

    @Resource
    private SeckillMqFailMapper mqFailMapper;

    @Resource
    private ObjectMapper objectMapper;

    @KafkaListener(topics = "seckill-order-topic", groupId = "seckill-order-group")
    public void consume(SeckillOrderMsg msg, Acknowledgment ack) {
        String orderId = msg.getOrderId();

        try {
            // 幂等性校验：检查订单是否已存在
            if (orderService.isOrderExists(orderId)) {
                log.info("Order {} already exists, skip duplicate message", orderId);
                ack.acknowledge();
                return;
            }

            // 第一层：内存指数退避重试，仅重试2次
            ExponentialBackoffUtil.executeWithRetry(() -> {
                orderService.createOrder(msg);
                return null;
            }, "create-order-" + orderId);

            ack.acknowledge();
            log.info("Order {} created successfully", orderId);

        } catch (Exception e) {
            log.error("Failed to process order {} after retries: {}", orderId, e.getMessage(), e);

            // 第二层：重试失败后落库到异常表
            try {
                String messageBody = objectMapper.writeValueAsString(msg);
                String errorStack = getStackTrace(e);

                SeckillMqFail mqFail = new SeckillMqFail();
                mqFail.setOrderId(orderId);
                mqFail.setMessageBody(messageBody);
                mqFail.setErrorStack(errorStack);
                mqFail.setCreateTime(new Date());
                mqFail.setRetryCount(0);
                mqFail.setStatus(0); // 0-未处理
                mqFail.setUpdateTime(new Date());

                mqFailMapper.insert(mqFail);

                // 触发监控告警（这里简化为日志记录）
                log.error("MQ消费失败，已写入异常表，orderId: {}, 请人工处理", orderId);

            } catch (Exception ex) {
                log.error("Failed to save mq fail record for order {}: {}", orderId, ex.getMessage());
            }

            // 不NACK，直接ACK，避免消息重回队列
            ack.acknowledge();
        }
    }

    private String getStackTrace(Throwable throwable) {
        StringBuilder sb = new StringBuilder();
        sb.append(throwable.toString()).append("\n");
        for (StackTraceElement element : throwable.getStackTrace()) {
            sb.append("\t").append(element.toString()).append("\n");
        }
        return sb.toString();
    }
}