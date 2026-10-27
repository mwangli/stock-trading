// AI_GENERATE_START -
package com.stock.model.repository;

import com.stock.model.entity.NewsFeatureSnapshotBackfillState;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

/**
 * 新闻日级特征快照回填状态仓储。
 *
 * @author mwangli
 * @since 2026-10-25
 */
@Repository
public interface NewsFeatureSnapshotBackfillStateRepository
        extends MongoRepository<NewsFeatureSnapshotBackfillState, String> {
}
// AI_GENERATE_END -
