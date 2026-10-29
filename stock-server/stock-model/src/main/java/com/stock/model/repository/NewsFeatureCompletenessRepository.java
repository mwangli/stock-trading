// AI_GENERATE_START --
package com.stock.model.repository;

import com.stock.model.entity.NewsFeatureCompletenessDocument;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

/**
 * 新闻特征来源日期完备性水位仓储。
 *
 * @author mwangli
 * @since 2026-10-28
 */
@Repository
public interface NewsFeatureCompletenessRepository
        extends MongoRepository<NewsFeatureCompletenessDocument, String> {

    /**
     * 批量查询指定特征版本和日期集合的完备性水位。
     *
     * @param featureVersion 新闻特征版本
     * @param marketDataSource 模型行情数据来源
     * @param marketDataSourceVersion 模型行情数据版本
     * @param featureTradeDates 特征交易日集合
     * @return 匹配的日期水位
     */
    List<NewsFeatureCompletenessDocument>
            findByFeatureVersionAndMarketDataSourceAndMarketDataSourceVersionAndFeatureTradeDateIn(
                    String featureVersion, String marketDataSource, String marketDataSourceVersion,
                    Collection<LocalDate> featureTradeDates);
}
// AI_GENERATE_END --
