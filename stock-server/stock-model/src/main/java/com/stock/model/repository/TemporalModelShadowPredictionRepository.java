// AI_GENERATE_START ------
package com.stock.model.repository;

import com.stock.model.entity.TemporalModelShadowPredictionDocument;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

/**
 * MASTER 影子预测仓储。
 *
 * @author mwangli
 * @since 2026-10-24
 */
@Repository
public interface TemporalModelShadowPredictionRepository
        extends MongoRepository<TemporalModelShadowPredictionDocument, String> {

    /**
     * 查询指定模型和目标交易日的全部影子预测。
     *
     * @param modelId MASTER 模型文档 ID
     * @param targetTradeDate 目标交易日
     * @return 影子预测列表
     */
    List<TemporalModelShadowPredictionDocument> findByModelIdAndTargetTradeDate(
            String modelId, LocalDate targetTradeDate);

    /**
     * 判断指定基线批次是否已生成 MASTER 影子结果。
     *
     * @param baselineBatchId Ranking LSTM 基线批次 ID
     * @param modelId MASTER 模型文档 ID
     * @return 是否存在
     */
    boolean existsByBaselineBatchIdAndModelId(String baselineBatchId, String modelId);

    /**
     * 查询指定基线批次的全部 MASTER 影子预测。
     *
     * @param baselineBatchId Ranking LSTM 基线批次 ID
     * @return MASTER 影子预测
     */
    List<TemporalModelShadowPredictionDocument> findByBaselineBatchId(String baselineBatchId);

    /**
     * 查询候选模型最近已评估的影子预测，用于滚动指标和权重更新。
     *
     * @param modelId MASTER 模型文档 ID
     * @param evaluationStatus 评估状态
     * @param pageable 日期倒序分页限制
     * @return 影子评估记录
     */
    List<TemporalModelShadowPredictionDocument> findByModelIdAndEvaluationStatusOrderByTargetTradeDateDesc(
            String modelId, String evaluationStatus, Pageable pageable);

    /**
     * 分页查询候选模型尚未完成后验评估的影子预测。
     *
     * @param modelId MASTER 模型文档 ID
     * @param evaluationStatus 评估状态
     * @param pageable 分页限制
     * @return 待评估影子预测
     */
    List<TemporalModelShadowPredictionDocument> findByModelIdAndEvaluationStatusOrderByPredictedAtAsc(
            String modelId, String evaluationStatus, Pageable pageable);
}
// AI_GENERATE_END ------
