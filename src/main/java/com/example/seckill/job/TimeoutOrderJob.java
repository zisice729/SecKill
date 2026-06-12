package com.example.seckill.job;

import com.example.seckill.repository.RedisRepository;
import com.example.seckill.service.OrderTimeoutService;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.UUID;

/**
 * 超时关单定时任务
 * 按照XXL-Job规范：外层类后缀Job，内部执行器后缀Handler
 */
@Slf4j
@Component
public class TimeoutOrderJob {

    @Resource
    private OrderTimeoutService orderTimeoutService;

    @Resource
    private RedisRepository redisRepository;

    // 分布式锁key
    private static final String LOCK_KEY = "seckill:lock:timeout-job";
    // 锁超时时间（秒）
    private static final int LOCK_EXPIRE = 120;

    /**
     * 超时关单执行器
     * Job层只做参数校验、调度幂等，业务逻辑下沉至Service
     */
    @XxlJob("timeoutOrderJobHandler")
    public void timeoutOrderJobHandler() throws Exception {
        String param = XxlJobHelper.getJobParam();
        log.info("TimeoutOrderJob started, param: {}", param);

        long startTime = System.currentTimeMillis();

        // 分布式幂等锁，防止集群重复调度
        String lockValue = UUID.randomUUID().toString();
        Boolean locked = redisRepository.tryLock(LOCK_KEY, lockValue, LOCK_EXPIRE);

        if (!Boolean.TRUE.equals(locked)) {
            log.warn("TimeoutOrderJob is already running on another node, skip");
            XxlJobHelper.handleFail("任务正在其他节点执行");
            return;
        }

        try {
            // 内置2次自动重试
            int retryCount = 0;
            Exception lastException = null;
            boolean success = false;

            while (retryCount < 2 && !success) {
                try {
                    orderTimeoutService.processTimeoutOrders();
                    success = true;
                } catch (Exception e) {
                    lastException = e;
                    retryCount++;
                    log.warn("TimeoutOrderJob retry {}/2 failed: {}", retryCount, e.getMessage());
                    if (retryCount < 2) {
                        Thread.sleep(1000L * retryCount);
                    }
                }
            }

            if (!success) {
                log.error("TimeoutOrderJob failed after {} retries: {}", retryCount, lastException.getMessage(), lastException);
                // 任务失败写入补偿表、推送告警（简化为日志记录）
                sendAlert("TimeoutOrderJob", "任务执行失败: " + lastException.getMessage());
                XxlJobHelper.handleFail(lastException.getMessage());
            } else {
                long duration = System.currentTimeMillis() - startTime;
                log.info("TimeoutOrderJob completed successfully, duration: {}ms", duration);
                XxlJobHelper.handleSuccess("任务执行成功");
            }

        } finally {
            // 释放分布式锁
            redisRepository.unlock(LOCK_KEY, lockValue);
        }
    }

    /**
     * 发送告警通知（简化实现）
     */
    private void sendAlert(String jobName, String message) {
        // 实际项目中可以集成钉钉、企业微信等告警通知
        log.error("ALERT: {} - {}", jobName, message);
    }
}