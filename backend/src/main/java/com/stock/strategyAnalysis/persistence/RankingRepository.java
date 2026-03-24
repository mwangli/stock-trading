package com.stock.strategyAnalysis.persistence;

import com.stock.strategyAnalysis.domain.entity.StockRanking;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

/**
 * 股票排名Repository
 * 使用MongoDB存储排名结果
 */
@Repository
public interface RankingRepository extends MongoRepository<StockRanking, String> {

    /**
     * 根据股票代码查询最新排名
     */
    StockRanking findFirstByStockCodeOrderByCalculateTimeDesc(String stockCode);

    /**
     * 根据计算日期查询排名列表
     */
    List<StockRanking> findByCalculateTimeBetween(
            LocalDate startTime,
            LocalDate endTime
    );

    /**
     * 查询指定日期的Top N排名
     */
    List<StockRanking> findTop10ByCalculateTimeOrderByRankAsc(LocalDate calculateTime);

    /**
     * 根据股票代码和日期查询排名
     */
    List<StockRanking> findByStockCodeAndCalculateTime(String stockCode, LocalDate calculateTime);
}
