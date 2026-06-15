package com.example.seckill.mapper;

import com.example.seckill.entity.Order;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 订单Mapper接口
 */
@Mapper
public interface OrderMapper {

    /**
     * 插入订单
     * @param order 订单信息
     * @return 影响行数
     */
    int insert(Order order);

    /**
     * 根据订单ID查询订单
     * @param orderId 订单ID
     * @return 订单信息
     */
    Order selectByOrderId(@Param("orderId") String orderId);

    /**
     * 根据用户ID和商品ID查询订单（幂等性检查）
     * @param userId 用户ID
     * @param goodsId 商品ID
     * @return 订单信息
     */
    Order selectByUserAndGoods(@Param("userId") Long userId, @Param("goodsId") Long goodsId);

    /**
     * 支付订单（乐观锁更新）
     * @param orderId 订单ID
     * @param outPayNo 外部支付流水号
     * @param expectedStatus 期望状态
     * @return 影响行数
     */
    int payOrder(@Param("orderId") String orderId, @Param("outPayNo") String outPayNo, @Param("expectedStatus") String expectedStatus);

    /**
     * 关闭订单（乐观锁更新）
     * @param orderId 订单ID
     * @param expectedStatus 期望状态
     * @return 影响行数
     */
    int closeOrder(@Param("orderId") String orderId, @Param("expectedStatus") String expectedStatus);

    /**
     * 查询超时未支付订单
     * @return 超时订单列表
     */
    java.util.List<Order> selectTimeoutOrders();
}