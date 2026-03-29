package com.stock.modelService.persistence;

import com.stock.modelService.domain.entity.SentimentEvaluation;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 情感模型评估结果仓库
 * 负责对 MongoDB 中的 {@link SentimentEvaluation} 文档进行 CRUD 操作。
 *
 * <p>主要用于查询历史评估记录、按版本筛选评估结果以及时间范围统计。</p>
 *
 * @author mwangli
 * @since 2026-03-29
 */
@Repository
public interface SentimentEvaluationRepository extends MongoRepository<SentimentEvaluation, String> {

    /**
     * 获取最新一次评估结果
     *
     * @return 最新评估记录
     */
    Optional<SentimentEvaluation> findTopByOrderByCreatedAtDesc();

    /**
     * 按模型版本查询所有评估记录
     *
     * @param modelVersion 模型版本号
     * @return 该版本的所有评估记录列表
     */
    List<SentimentEvaluation> findByModelVersion(String modelVersion);

    /**
     * 按时间范围查询评估记录
     *
     * @param start 起始时间（包含）
     * @param end   结束时间（包含）
     * @return 时间范围内的评估记录列表
     */
    List<SentimentEvaluation> findByCreatedAtBetween(LocalDateTime start, LocalDateTime end);
}