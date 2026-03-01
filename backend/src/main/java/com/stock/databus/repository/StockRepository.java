package com.stock.databus.repository;

import com.stock.databus.entity.StockInfo;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 股票信息数据访问接口
 */
@Repository
public interface StockRepository extends MongoRepository<StockInfo, String> {

    /**
     * 根据股票代码查询
     */
    Optional<StockInfo> findByCode(String code);

    /**
     * 查询所有股票
     */
    List<StockInfo> findAllByOrderByCodeAsc();

    /**
     * 检查股票是否存在
     */
    boolean existsByCode(String code);

    /**
     * 根据股票名称模糊查询
     */
    List<StockInfo> findByNameContaining(String name);
}