package com.example.seckill.repository.mapper;

import com.example.seckill.repository.entity.SeckillGoods;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface SeckillGoodsMapper {

    SeckillGoods selectByPrimaryKey(Long id);

    int decreaseStock(@Param("goodsId") Long goodsId, @Param("quantity") Integer quantity);

    int decreaseStockWithVersion(@Param("goodsId") Long goodsId, @Param("quantity") Integer quantity);

    int increaseStock(@Param("goodsId") Long goodsId, @Param("quantity") Integer quantity);

}