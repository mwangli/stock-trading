// AI_GENERATE_START-
package com.stock.data.service;

import com.mongodb.client.result.DeleteResult;
import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 清理市场数据模块内超过保留期的新闻事实。
 *
 * @author mwangli
 * @since 2026-10-27
 */
@Service
@RequiredArgsConstructor
public class MarketDataCleanupService {

    private static final int NEWS_RETENTION_DAYS = 365;
    private static final int NEWS_DERIVED_RETENTION_DAYS = 730;

    private final MongoTemplate mongoTemplate;

    /**
     * 清理超过保留期的原始新闻。
     *
     * @return 删除记录数
     */
    public long cleanupExpiredNews() {
        return removeBefore("stock_news", "publishTime",
                LocalDateTime.now().minusDays(NEWS_RETENTION_DAYS));
    }

    /**
     * 清理超过保留期的新闻事件事实。
     *
     * @return 删除记录数
     */
    public long cleanupExpiredNewsEventFacts() {
        return removeBefore("stock_news_event_facts", "publishTime",
                LocalDateTime.now().minusDays(NEWS_DERIVED_RETENTION_DAYS));
    }

    private long removeBefore(String collectionName, String fieldName, Object cutoff) {
        if (!mongoTemplate.collectionExists(collectionName)) {
            return 0L;
        }
        DeleteResult result = mongoTemplate.remove(
                Query.query(Criteria.where(fieldName).lt(cutoff)), collectionName);
        return result.getDeletedCount();
    }
}
// AI_GENERATE_END-