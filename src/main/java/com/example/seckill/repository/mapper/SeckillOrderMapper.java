package com.example.seckill.repository.mapper;

import com.example.seckill.repository.entity.SeckillOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface SeckillOrderMapper {

    SeckillOrder selectByOrderId(String orderId);

    int insert(SeckillOrder record);

    int updateStatus(@Param("orderId") String orderId, @Param("status") Integer status);

    int closeOrder(@Param("orderId") String orderId, @Param("status") Integer status);

    List<SeckillOrder> selectTimeoutUnpaidOrders();

}