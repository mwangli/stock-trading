// AI_GENERATE_START--
package com.stock.market.repository;

import com.stock.market.entity.NewsCollectionBackfillState;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

/**
 * 新闻采集治理历史回填状态仓储。
 *
 * @author mwangli
 * @since 2026-10-23
 */
@Repository
public interface NewsCollectionBackfillStateRepository
        extends MongoRepository<NewsCollectionBackfillState, String> {
}
// AI_GENERATE_END--
