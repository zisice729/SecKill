package com.example.seckill.service.impl;

import com.alibaba.fastjson.JSON;
import com.example.seckill.constant.OrderStatus;
import com.example.seckill.dto.KafkaMsgDTO;
import com.example.seckill.entity.Order;
import com.example.seckill.entity.SeckillMqFailLog;
import com.example.seckill.mapper.GoodsMapper;
import com.example.seckill.mapper.OrderMapper;
import com.example.seckill.mapper.SeckillMqFailLogMapper;
import com.example.seckill.service.KafkaConsumerService;
import com.example.seckill.util.IdUtil;
import com.example.seckill.util.RetryUtil;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Kafka消费者实现类
 */
@Service
public class KafkaConsumerImpl implements KafkaConsumerService {

    private final OrderMapper orderMapper;
    private final GoodsMapper goodsMapper;
    private final SeckillMqFailLogMapper failLogMapper;
    private final IdUtil idUtil;
    private final RetryUtil retryUtil;

    // 本地最大重试2次，间隔 1s、3s
    private static final int LOCAL_MAX_RETRY = 2;
    private static final long[] LOCAL_DELAYS = {1000, 3000};

    public KafkaConsumerImpl(OrderMapper orderMapper,
                             GoodsMapper goodsMapper,
                             SeckillMqFailLogMapper failLogMapper,
                             IdUtil idUtil,
                             RetryUtil retryUtil) {
        this.orderMapper = orderMapper;
        this.goodsMapper = goodsMapper;
        this.failLogMapper = failLogMapper;
        this.idUtil = idUtil;
        this.retryUtil = retryUtil;
    }

    /**
     * Kafka消息监听器
     */
    @KafkaListener(topics = "seckill_topic", groupId = "seckill_group")
    public void listen(ConsumerRecord<String, KafkaMsgDTO> record, Acknowledgment ack) {
        KafkaMsgDTO msg = record.value();
        Long userId = msg.getUserId();
        Long goodsId = msg.getGoodsId();

        try {
            // 幂等判断：根据 user+goods 查询
            Order existOrder = orderMapper.selectByUserAndGoods(userId, goodsId);
            if (existOrder != null) {
                ack.acknowledge();
                return;
            }

            // 本地重试执行建单+扣库存
            Boolean success = retryUtil.retry(LOCAL_MAX_RETRY, LOCAL_DELAYS,
                    () -> createOrderAndStock(userId, goodsId));

            if (Boolean.TRUE.equals(success)) {
                ack.acknowledge();
            } else {
                saveFailLog(msg, "本地重试2次执行失败");
                ack.acknowledge();
            }
        } catch (Exception e) {
            saveFailLog(msg, e.getMessage());
            ack.acknowledge();
        }
    }

    @Override
    public void consume(Long userId, Long goodsId) {
        // 此方法为接口实现，实际消费逻辑在listen方法中
    }

    /**
     * 事务：消费端生成订单号，创建订单 + 扣真实库存
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean createOrderAndStock(Long userId, Long goodsId) {
        String orderId = idUtil.nextOrderId();
        Order order = new Order();
        order.setOrderId(orderId);
        order.setUserId(userId);
        order.setGoodsId(goodsId);
        order.setStatus(OrderStatus.UN_PAY);
        order.setPayDeadline(LocalDateTime.now().plusMinutes(15));
        orderMapper.insert(order);

        int rows = goodsMapper.deductStock(goodsId);
        if (rows <= 0) {
            throw new RuntimeException("真实库存不足");
        }
        return true;
    }

    /**
     * 保存失败日志
     */
    private void saveFailLog(KafkaMsgDTO msg, String errorMsg) {
        SeckillMqFailLog log = new SeckillMqFailLog();
        log.setUserId(msg.getUserId());
        log.setGoodsId(msg.getGoodsId());
        log.setMsgContent(JSON.toJSONString(msg));
        log.setErrorMsg(errorMsg);
        log.setRetryCount(0);
        log.setHandleStatus(0);
        failLogMapper.insert(log);
        // 此处添加监控告警
    }
}