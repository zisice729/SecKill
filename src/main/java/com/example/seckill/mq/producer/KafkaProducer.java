package com.example.seckill.mq.producer;

import com.example.seckill.mq.msg.SeckillOrderMsg;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;
import org.springframework.util.concurrent.ListenableFuture;
import org.springframework.util.concurrent.ListenableFutureCallback;

import javax.annotation.Resource;

@Slf4j
@Component
public class KafkaProducer {

    @Resource
    private KafkaTemplate<String, SeckillOrderMsg> kafkaTemplate;

    private static final String TOPIC = "seckill-order-topic";

    public void sendOrderMsg(SeckillOrderMsg msg) {
        ListenableFuture<SendResult<String, SeckillOrderMsg>> future =
                kafkaTemplate.send(TOPIC, msg.getOrderId(), msg);

        future.addCallback(new ListenableFutureCallback<SendResult<String, SeckillOrderMsg>>() {
            @Override
            public void onSuccess(SendResult<String, SeckillOrderMsg> result) {
                log.info("Message sent successfully: {}", msg.getOrderId());
            }

            @Override
            public void onFailure(Throwable ex) {
                log.error("Failed to send message: {}", msg.getOrderId(), ex);
            }
        });
    }
}