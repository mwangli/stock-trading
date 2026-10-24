// AI_GENERATE_START-----
package com.stock.market.repository;

import com.stock.market.entity.ModelReferenceDailyBar;
import com.stock.market.entity.ReferenceSeriesType;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

/**
 * 市场和行业参考序列仓储。
 * 为模型特征和策略自动切换提供只读的版本化日线数据。
 *
 * @author mwangli
 * @since 2026-10-13
 */
@Repository
public interface ModelReferenceDailyBarRepository extends MongoRepository<ModelReferenceDailyBar, String> {

    /**
     * 查询最近的市场参考序列日线，调用方按序列代码分组后选择完整序列。
     *
     * @param seriesType 参考序列类型
     * @return 最近的参考序列数据
     */
    List<ModelReferenceDailyBar> findTop100BySeriesTypeOrderByTradeDateDesc(ReferenceSeriesType seriesType);

    /**
     * 查询指定参考序列和数据版本的完整日线。
     *
     * @param seriesType 参考序列类型
     * @param seriesCode 参考序列代码
     * @param source 数据来源
     * @param sourceVersion 数据版本
     * @return 按交易日升序排列的参考日线
     */
    List<ModelReferenceDailyBar> findBySeriesTypeAndSeriesCodeAndSourceAndSourceVersionOrderByTradeDateAsc(
            ReferenceSeriesType seriesType, String seriesCode, String source, String sourceVersion);

    /**
     * 查询截止指定日期的参考序列，供历史推理和时间可用性校验使用。
     *
     * @param seriesType 参考序列类型
     * @param seriesCode 参考序列代码
     * @param endDate 截止日期，包含当天
     * @param source 数据来源
     * @param sourceVersion 数据版本
     * @return 按交易日升序排列的参考日线
     */
    List<ModelReferenceDailyBar>
            findBySeriesTypeAndSeriesCodeAndTradeDateLessThanEqualAndSourceAndSourceVersionOrderByTradeDateAsc(
                    ReferenceSeriesType seriesType, String seriesCode, LocalDate endDate,
                    String source, String sourceVersion);
}
// AI_GENERATE_END-----
