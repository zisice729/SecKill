package com.example.seckill.util;

import org.springframework.stereotype.Component;

/**
 * 订单号生成工具类
 * 使用雪花算法生成全局唯一订单号
 */
@Component
public class IdUtil {

    private final SnowflakeIdGenerator snowflakeIdGenerator;

    public IdUtil(SnowflakeIdGenerator snowflakeIdGenerator) {
        this.snowflakeIdGenerator = snowflakeIdGenerator;
    }

    /**
     * 生成下一个订单ID
     * @return 订单ID
     */
    public String nextOrderId() {
        return String.valueOf(snowflakeIdGenerator.nextId());
    }
}