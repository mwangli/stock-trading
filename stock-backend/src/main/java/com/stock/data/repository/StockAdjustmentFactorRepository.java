// AI_GENERATE_START --
package com.stock.data.repository;

import com.stock.data.entity.StockAdjustmentFactor;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

/**
 * 股票复权因子仓储。
 * 为模型训练批量提供固定来源版本的前复权因子。
 *
 * @author mwangli
 * @since 2026-10-23
 */
@Repository
public interface StockAdjustmentFactorRepository extends MongoRepository<StockAdjustmentFactor, String> {

    /**
     * 批量查询指定股票和数据版本的复权因子。
     *
     * @param stockCodes 股票代码集合
     * @param source 数据来源
     * @param sourceVersion 数据版本
     * @return 按股票和日期排序的复权因子
     */
    List<StockAdjustmentFactor> findByStockCodeInAndSourceAndSourceVersionOrderByStockCodeAscTradeDateAsc(
            Collection<String> stockCodes, String source, String sourceVersion);

    /**
     * 查询截止指定日期的复权因子。
     *
     * @param stockCodes 股票代码集合
     * @param endDate 截止日期，包含当天
     * @param source 数据来源
     * @param sourceVersion 数据版本
     * @return 按股票和日期排序的复权因子
     */
    List<StockAdjustmentFactor>
            findByStockCodeInAndTradeDateLessThanEqualAndSourceAndSourceVersionOrderByStockCodeAscTradeDateAsc(
                    Collection<String> stockCodes, LocalDate endDate, String source, String sourceVersion);
}
// AI_GENERATE_END --
