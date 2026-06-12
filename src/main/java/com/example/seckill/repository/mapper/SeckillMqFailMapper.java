package com.example.seckill.repository.mapper;

import com.example.seckill.repository.entity.SeckillMqFail;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface SeckillMqFailMapper {

    int insert(SeckillMqFail record);

    SeckillMqFail selectByOrderId(String orderId);

    List<SeckillMqFail> selectPendingRetry(@Param("minutes") int minutes);

    int updateRetryCount(@Param("id") Long id, @Param("retryCount") Integer retryCount);

    int updateStatus(@Param("id") Long id, @Param("status") Integer status);

}