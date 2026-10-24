// AI_GENERATE_START ---
package com.stock.model.repository;

import com.stock.model.entity.SentimentShadowPredictionDocument;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Mengzi 情感影子预测仓储。
 *
 * @author mwangli
 * @since 2026-10-24
 */
@Repository
public interface SentimentShadowPredictionRepository
        extends MongoRepository<SentimentShadowPredictionDocument, String> {

    /**
     * 判断事件事实是否已有指定候选版本的影子预测。
     *
     * @param eventFactId 事件事实 ID
     * @param candidateModelVersion 候选模型版本
     * @return 是否已经存在
     */
    boolean existsByEventFactIdAndCandidateModelVersion(
            String eventFactId, String candidateModelVersion);

    /**
     * 查询指定候选版本已完成评估的记录。
     *
     * @param candidateModelVersion 候选模型版本
     * @param evaluationStatus 评估状态
     * @return 影子评估记录
     */
    List<SentimentShadowPredictionDocument> findByCandidateModelVersionAndEvaluationStatus(
            String candidateModelVersion, String evaluationStatus);

    /**
     * 分页查询指定候选版本等待评估的记录，避免影子任务扫描全表。
     *
     * @param candidateModelVersion 候选模型版本
     * @param evaluationStatus 评估状态
     * @param pageable 分页限制
     * @return 待评估记录
     */
    List<SentimentShadowPredictionDocument> findByCandidateModelVersionAndEvaluationStatusOrderByPredictedAtAsc(
            String candidateModelVersion, String evaluationStatus, Pageable pageable);
}
// AI_GENERATE_END ---
