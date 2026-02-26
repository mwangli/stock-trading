package com.stock.databus.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.stock.databus.entity.StockInfo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface StockRepository extends BaseMapper<StockInfo> {

    @Select("SELECT * FROM stock_info WHERE is_tradable = 1 AND `delete` = '0' LIMIT #{limit}")
    List<StockInfo> findTradableStocks(@Param("limit") int limit);

    @Select("SELECT * FROM stock_info WHERE code = #{code}")
    StockInfo findByCode(@Param("code") String code);

    @Select("SELECT COUNT(*) FROM stock_info WHERE `delete` = '0'")
    int countAll();

    @Select("SELECT * FROM stock_info WHERE market = #{market} AND is_tradable = 1 AND `delete` = '0'")
    List<StockInfo> findByMarket(@Param("market") String market);
}
