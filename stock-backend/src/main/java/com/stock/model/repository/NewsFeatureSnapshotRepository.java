// AI_GENERATE_START -
package com.stock.model.repository;

import com.stock.model.entity.NewsFeatureSnapshotDocument;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

/**
 * 日级新闻时序特征快照仓储。
 *
 * @author mwangli
 * @since 2026-10-24
 */
@Repository
public interface NewsFeatureSnapshotRepository extends MongoRepository<NewsFeatureSnapshotDocument, String> {

    /**
     * 批量查询指定版本和日期范围的股票新闻特征快照。
     *
     * @param featureVersion 新闻特征版本
     * @param stockCodes 股票代码集合
     * @param startDate 起始交易日
     * @param endDate 结束交易日
     * @return 匹配的日级快照
     */
    List<NewsFeatureSnapshotDocument> findByFeatureVersionAndStockCodeInAndFeatureTradeDateBetween(
            String featureVersion, Collection<String> stockCodes, LocalDate startDate, LocalDate endDate);
}
// AI_GENERATE_END -
