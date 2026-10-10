// AI_GENERATE_START --
package com.stock.strategyAnalysis.persistence;

import com.stock.strategyAnalysis.domain.entity.StockRanking;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

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
    Optional<StockRanking> findFirstByCalculateTimeBetweenOrderByCalculateTimeDesc(
            LocalDateTime startTime,
            LocalDateTime endTime
    );

    /**
     * 查询同一批次的完整排名。
     *
     * @param calculateTime 批次计算时间
     * @return 按排名升序排列的候选
     */
    List<StockRanking> findByCalculateTimeOrderByRankAsc(LocalDateTime calculateTime);

    /**
     * 根据股票代码和日期查询排名
     */
    List<StockRanking> findByStockCodeAndCalculateTime(String stockCode, LocalDate calculateTime);
}
// AI_GENERATE_END --
