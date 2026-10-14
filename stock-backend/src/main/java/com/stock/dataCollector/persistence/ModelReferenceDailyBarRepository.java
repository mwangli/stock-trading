// AI_GENERATE_START
package com.stock.dataCollector.persistence;

import com.stock.dataCollector.domain.entity.ModelReferenceDailyBar;
import com.stock.dataCollector.domain.entity.ReferenceSeriesType;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

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
}
// AI_GENERATE_END
