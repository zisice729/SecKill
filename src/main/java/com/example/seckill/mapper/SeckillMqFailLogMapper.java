package com.example.seckill.mapper;

import com.example.seckill.entity.SeckillMqFailLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * Kafka消费异常日志Mapper接口
 */
@Mapper
public interface SeckillMqFailLogMapper {

    /**
     * 插入异常日志
     * @param log 异常日志
     * @return 影响行数
     */
    int insert(SeckillMqFailLog log);

    /**
     * 查询未处理的异常日志
     * @param maxRetry 最大重试次数
     * @return 异常日志列表
     */
    List<SeckillMqFailLog> selectUnHandleLog(@Param("maxRetry") int maxRetry);

    /**
     * 更新重试次数
     * @param id 日志ID
     * @param retryCount 重试次数
     * @return 影响行数
     */
    int updateRetryCount(@Param("id") Long id, @Param("retryCount") int retryCount);

    /**
     * 标记为已处理
     * @param id 日志ID
     * @return 影响行数
     */
    int markAsProcessed(@Param("id") Long id);

    /**
     * 标记为人工处理
     * @param id 日志ID
     * @return 影响行数
     */
    int markAsManual(@Param("id") Long id);
}