package com.stock.strategyAnalysis.repository;

import com.stock.strategyAnalysis.entity.SellDecision;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

/**
 * 卖出决策Repository
 * 使用MongoDB存储决策记录
 */
@Repository
public interface DecisionRepository extends MongoRepository<SellDecision, String> {
    
    /**
     * 根据股票代码查询最新决策
     */
    SellDecision findFirstByStockCodeOrderByTimestampDesc(String stockCode);
    
    /**
     * 根据股票代码和日期查询决策
     */
    List<SellDecision> findByStockCodeAndTimestampBetween(
            String stockCode, 
            LocalDate startTime, 
            LocalDate endTime
    );
    
    /**
     * 查询需要卖出的决策
     */
    List<SellDecision> findByShouldSellTrue();
    
    /**
     * 根据日期范围查询所有决策
     */
    List<SellDecision> findByTimestampBetween(LocalDate startTime, LocalDate endTime);
}