// AI_GENERATE_START--------------
package com.stock.strategy.repository;

import com.stock.strategy.entity.StockRanking;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 股票排名Repository
 * 使用MongoDB存储排名结果
 *
 * @author mwangli
 * @since 2026-10-14
 */
@Repository
public interface RankingRepository extends MongoRepository<StockRanking, String> {

    /**
     * 根据股票代码查询最新排名。
     *
     * @param stockCode 股票代码
     * @return 最新排名，不存在时返回 null
     */
    StockRanking findFirstByStockCodeOrderByCalculateTimeDesc(String stockCode);

    /**
     * 根据计算日期范围查询排名列表。
     *
     * @param startTime 起始日期
     * @param endTime 结束日期
     * @return 区间内排名列表
     */
    List<StockRanking> findByCalculateTimeBetween(
            LocalDate startTime,
            LocalDate endTime
    );

    /**
     * 查询指定时间范围内最新排名。
     *
     * @param startTime 起始时间
     * @param endTime 结束时间
     * @return 最新排名
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
     * 查询目标交易日最新候选记录。
     *
     * @param targetTradeDate 目标交易日
     * @return 最新候选记录
     */
    Optional<StockRanking> findFirstByTargetTradeDateOrderByCalculateTimeDesc(LocalDate targetTradeDate);

    /**
     * 查询可执行候选批次的完整排名。
     *
     * @param candidateBatchId 候选批次 ID
     * @param status 候选状态
     * @return 按排名升序排列的候选
     */
    List<StockRanking> findByCandidateBatchIdAndStatusOrderByRankAsc(String candidateBatchId, String status);

    /**
     * 查询候选批次的完整评分排名。
     *
     * @param candidateBatchId 候选批次 ID
     * @return 按排名升序排列的完整评分列表
     */
    List<StockRanking> findByCandidateBatchIdOrderByRankAsc(String candidateBatchId);

    /**
     * 删除指定预测批次产生的候选排名。
     *
     * @param predictionBatchId 预测批次 ID
     * @return 删除的排名数量
     */
    long deleteByPredictionBatchId(String predictionBatchId);

    /**
     * 查询评估窗口内的历史候选，按目标交易日和排名排序。
     *
     * @param startDate 评估窗口起始日
     * @param endDate 评估窗口结束日
     * @return 历史候选快照
     */
    List<StockRanking> findByTargetTradeDateBetweenOrderByTargetTradeDateAscRankAsc(
            LocalDate startDate, LocalDate endDate);

    /**
     * 根据股票代码和计算日期查询排名。
     *
     * @param stockCode 股票代码
     * @param calculateTime 计算日期
     * @return 匹配的排名列表
     */
    List<StockRanking> findByStockCodeAndCalculateTime(String stockCode, LocalDate calculateTime);
}
// AI_GENERATE_END--------------
