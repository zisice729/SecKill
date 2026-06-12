package com.example.seckill.common.util;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ExponentialBackoffUtil {

    private static final long INITIAL_INTERVAL_MS = 100;
    private static final double MULTIPLIER = 2.0;
    private static final long MAX_INTERVAL_MS = 10000;
    private static final int MAX_RETRY_ATTEMPTS = 5;

    public static long calculateWaitTime(int attempt) {
        if (attempt <= 0) {
            return INITIAL_INTERVAL_MS;
        }
        double waitTime = INITIAL_INTERVAL_MS * Math.pow(MULTIPLIER, attempt - 1);
        return Math.min((long) waitTime, MAX_INTERVAL_MS);
    }

    public static <T> T executeWithRetry(Callable<T> task, String taskName) throws Exception {
        Exception lastException = null;

        for (int attempt = 1; attempt <= MAX_RETRY_ATTEMPTS; attempt++) {
            try {
                return task.call();
            } catch (Exception e) {
                lastException = e;
                log.warn("Task {} failed on attempt {}/{}: {}", taskName, attempt, MAX_RETRY_ATTEMPTS, e.getMessage());

                if (attempt < MAX_RETRY_ATTEMPTS) {
                    long waitTime = calculateWaitTime(attempt);
                    Thread.sleep(waitTime);
                    log.info("Retrying task {} after {}ms", taskName, waitTime);
                }
            }
        }

        log.error("Task {} failed after {} attempts", taskName, MAX_RETRY_ATTEMPTS);
        throw lastException;
    }

    @FunctionalInterface
    public interface Callable<T> {
        T call() throws Exception;
    }
}