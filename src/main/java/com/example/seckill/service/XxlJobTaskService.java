package com.example.seckill.service;

/**
 * XXL-Job定时任务服务接口
 */
public interface XxlJobTaskService {

    /**
     * Kafka异常记录补偿重试任务
     */
    void mqFailRetryJob();

    /**
     * 超时关单任务
     */
    void timeoutCloseOrderJob();
}