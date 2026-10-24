// AI_GENERATE_START--
package com.stock.market.repository;

import com.stock.market.entity.ModelStockDailyBar;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.time.LocalDate;

/**
 * 模型专用股票日线仓储。
 * 按明确的数据来源和版本读取不可覆盖的训练行情。
 *
 * @author mwangli
 * @since 2026-10-23
 */
@Repository
public interface ModelStockDailyBarRepository extends MongoRepository<ModelStockDailyBar, String> {

    /**
     * 查询指定股票和数据版本的最近日线。
     *
     * @param stockCode 股票代码
     * @param source 数据来源
     * @param sourceVersion 数据版本
     * @param pageable 查询窗口
     * @return 按交易日倒序排列的模型日线
     */
    List<ModelStockDailyBar> findByStockCodeAndSourceAndSourceVersionOrderByTradeDateDesc(
            String stockCode, String source, String sourceVersion, Pageable pageable);

    /**
     * 查询截止指定日期的模型日线窗口。
     *
     * @param stockCode 股票代码
     * @param endDate 截止日期，包含当天
     * @param source 数据来源
     * @param sourceVersion 数据版本
     * @param pageable 查询窗口
     * @return 按交易日倒序排列的模型日线
     */
    List<ModelStockDailyBar> findByStockCodeAndTradeDateLessThanEqualAndSourceAndSourceVersionOrderByTradeDateDesc(
            String stockCode, LocalDate endDate, String source, String sourceVersion, Pageable pageable);

    /**
     * 查询固定来源版本的最新模型日线。
     *
     * @param source 数据来源
     * @param sourceVersion 数据版本
     * @return 最新模型日线
     */
    Optional<ModelStockDailyBar> findFirstBySourceAndSourceVersionOrderByTradeDateDesc(
            String source, String sourceVersion);
}
// AI_GENERATE_END--
