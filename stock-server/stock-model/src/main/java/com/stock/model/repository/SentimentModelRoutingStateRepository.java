// AI_GENERATE_START --
package com.stock.model.repository;

import com.stock.model.entity.SentimentModelRoutingStateDocument;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

/**
 * 情感模型路由状态仓储。
 *
 * @author mwangli
 * @since 2026-10-24
 */
@Repository
public interface SentimentModelRoutingStateRepository
        extends MongoRepository<SentimentModelRoutingStateDocument, String> {
}
// AI_GENERATE_END --
