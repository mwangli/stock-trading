// AI_GENERATE_START -
package com.stock.dataCollector.persistence;

import com.stock.dataCollector.domain.entity.StockDailyTradabilityFact;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
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
}
// AI_GENERATE_END -
