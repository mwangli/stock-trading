// AI_GENERATE_START-----
package com.stock.data.repository;

import com.stock.data.entity.StockDailyTradabilityFact;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 股票日内可成交性事实仓库。
 *
 * @author mwangli
 * @since 2026-09-25
 */
@Repository
public interface StockDailyTradabilityFactRepository extends MongoRepository<StockDailyTradabilityFact, String> {

    /**
     * 查询指定股票和交易日最新采集的可成交性事实。
     *
     * @param stockCode 股票代码
     * @param tradeDate 交易日期
     * @return 最新可成交性事实
     */
    Optional<StockDailyTradabilityFact> findFirstByStockCodeAndTradeDateOrderByCapturedAtDesc(
            String stockCode, LocalDate tradeDate);

    /**
     * 批量查询固定来源版本和日期区间内的可交易事实。
     *
     * @param stockCodes 股票代码集合
     * @param startDate 开始日期，包含当天
     * @param endDate 结束日期，包含当天
     * @param source 数据来源
     * @param sourceVersion 数据版本
     * @return 股票单日可交易事实
     */
    List<StockDailyTradabilityFact> findByStockCodeInAndTradeDateBetweenAndSourceAndSourceVersion(
            Collection<String> stockCodes, LocalDate startDate, LocalDate endDate,
            String source, String sourceVersion);
}
// AI_GENERATE_END-----
