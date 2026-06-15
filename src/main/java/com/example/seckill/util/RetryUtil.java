package com.example.seckill.util;

import org.springframework.stereotype.Component;

import java.util.function.Supplier;

/**
 * 通用重试工具类
 */
@Component
public class RetryUtil {

    /**
     * 间隔重试
     * @param maxRetry 最大重试次数
     * @param delays 间隔毫秒数组
     * @param task 业务逻辑
     * @return 执行结果
     */
    public <T> T retry(int maxRetry, long[] delays, Supplier<T> task) {
        int count = 0;
        T result = null;
        while (count <= maxRetry) {
            try {
                result = task.get();
                if (result != null && (result instanceof Boolean && (Boolean)result)) {
                    return result;
                }
            } catch (Exception e) {
                // 异常继续重试
            }
            if (count >= delays.length) {
                break;
            }
            try {
                Thread.sleep(delays[count]);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            count++;
        }
        return result;
    }
}