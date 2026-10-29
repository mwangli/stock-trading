// AI_GENERATE_START-
package com.stock.model.repository;

import com.stock.model.entity.SentimentEventCacheDocument;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

/**
 * 规范新闻事件情感推理缓存仓储。
 *
 * @author mwangli
 * @since 2026-10-29
 */
@Repository
public interface SentimentEventCacheRepository
        extends MongoRepository<SentimentEventCacheDocument, String> {
}
// AI_GENERATE_END-
