package com.example.seckill.mapper;

import com.example.seckill.entity.Goods;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 商品Mapper接口
 */
@Mapper
public interface GoodsMapper {

    /**
     * 根据ID查询商品
     * @param id 商品ID
     * @return 商品信息
     */
    Goods selectById(@Param("id") Long id);

    /**
     * 扣减库存（乐观锁）
     * @param id 商品ID
     * @return 影响行数
     */
    int deductStock(@Param("id") Long id);

    /**
     * 归还库存
     * @param id 商品ID
     * @return 影响行数
     */
    int restoreStock(@Param("id") Long id);
}