package com.stock.dataCollector.config;

import com.stock.dataCollector.domain.entity.StockNews;
import com.stock.modelService.domain.entity.SentimentEvaluation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.IndexOperations;

import jakarta.annotation.PostConstruct;
import java.util.concurrent.TimeUnit;

/**
 * MongoDB TTL 索引配置
 * <p>
 * 通过在关键时间字段上创建 TTL 索引，实现数据的自动过期清理，
 * 避免磁盘空间持续增长。
 * </p>
 *
 * @author mwangli
 * @since 2026-04-01
 */
@Slf4j
@Configuration
public class MongoTtlIndexConfig {

    private final MongoTemplate mongoTemplate;

    private static final int NEWS_RETENTION_DAYS = 180;
    private static final int EVALUATION_RETENTION_DAYS = 90;

    public MongoTtlIndexConfig(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @PostConstruct
    public void initTtlIndexes() {
        log.info("========== [MongoDB TTL] 开始初始化 TTL 索引 ==========");

        try {
            createNewsTtlIndex();
            createEvaluationTtlIndex();
            log.info("========== [MongoDB TTL] TTL 索引初始化完成 ==========");
        } catch (Exception e) {
            log.warn("[MongoDB TTL] TTL 索引初始化失败，将在后继运行时重试: {}", e.getMessage());
        }
    }

    /**
     * 新闻数据 TTL 索引
     * publishTime 字段 + 180 天后自动删除
     */
    private void createNewsTtlIndex() {
        try {
            IndexOperations indexOps = mongoTemplate.indexOps(StockNews.class);

            Index index = new Index()
                    .on("publishTime", org.springframework.data.domain.Sort.Direction.ASC)
                    .expire(NEWS_RETENTION_DAYS, TimeUnit.DAYS)
                    .named("idx_news_publishTime_ttl");

            indexOps.ensureIndex(index);
            log.info("[MongoDB TTL] stock_news TTL 索引创建成功: publishTime + {} 天", NEWS_RETENTION_DAYS);
        } catch (Exception e) {
            log.warn("[MongoDB TTL] stock_news TTL 索引创建失败: {}", e.getMessage());
        }
    }

    /**
     * 情感评估结果 TTL 索引
     * createdAt 字段 + 90 天后自动删除
     */
    private void createEvaluationTtlIndex() {
        try {
            IndexOperations indexOps = mongoTemplate.indexOps(SentimentEvaluation.class);

            Index index = new Index()
                    .on("createdAt", org.springframework.data.domain.Sort.Direction.ASC)
                    .expire(EVALUATION_RETENTION_DAYS, TimeUnit.DAYS)
                    .named("idx_evaluation_createdAt_ttl");

            indexOps.ensureIndex(index);
            log.info("[MongoDB TTL] sentiment_evaluation_results TTL 索引创建成功: createdAt + {} 天", EVALUATION_RETENTION_DAYS);
        } catch (Exception e) {
            log.warn("[MongoDB TTL] sentiment_evaluation_results TTL 索引创建失败: {}", e.getMessage());
        }
    }

    /**
     * 检查指定集合的 TTL 索引状态
     */
    public void checkTtlIndexes() {
        log.info("========== [MongoDB TTL] 检查索引状态 ==========");

        checkCollectionIndexes(StockNews.class, "stock_news");
        checkCollectionIndexes(SentimentEvaluation.class, "sentiment_evaluation_results");

        log.info("========== [MongoDB TTL] 索引状态检查完成 ==========");
    }

    private void checkCollectionIndexes(Class<?> entityClass, String collectionName) {
        try {
            IndexOperations indexOps = mongoTemplate.indexOps(entityClass);
            var indexes = indexOps.getIndexInfo();
            log.info("[MongoDB TTL] {} 索引列表: {}", collectionName, indexes);
        } catch (Exception e) {
            log.warn("[MongoDB TTL] 检查 {} 索引失败: {}", collectionName, e.getMessage());
        }
    }
}
