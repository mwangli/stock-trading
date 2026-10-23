// AI_GENERATE_START ------
package com.stock.modelService.persistence;

import com.stock.modelService.domain.entity.ModelPredictionDocument;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 单股模型预测结果仓库。
 *
 * @author mwangli
 * @since 2026-10-12
 */
@Repository
public interface ModelPredictionRepository extends MongoRepository<ModelPredictionDocument, String> {

    /**
     * 查询指定预测批次和股票的不可变预测明细。
     *
     * @param batchId 预测批次 ID
     * @param stockCode 股票代码
     * @return 匹配的预测明细
     */
    Optional<ModelPredictionDocument> findByBatchIdAndStockCode(String batchId, String stockCode);

    /**
     * 判断指定批次是否仍存在某评估状态的预测明细。
     *
     * @param batchId 预测批次 ID
     * @param evaluationStatus 评估状态
     * @return 是否存在匹配明细
     */
    boolean existsByBatchIdAndEvaluationStatus(String batchId, String evaluationStatus);

    /**
     * 统计指定批次实际保存的预测明细数量。
     *
     * @param batchId 预测批次 ID
     * @return 预测明细数量
     */
    long countByBatchId(String batchId);

    /**
     * 删除失败或取消批次留下的不完整预测明细。
     *
     * @param batchId 预测批次 ID
     */
    void deleteByBatchId(String batchId);

    /**
     * 分页查询指定批次预测明细。
     *
     * @param batchId 预测批次 ID
     * @param pageable 分页条件
     * @return 预测明细分页结果
     */
    Page<ModelPredictionDocument> findByBatchId(String batchId, Pageable pageable);

    /**
     * 按批次和评估状态分页查询预测明细。
     *
     * @param batchId 预测批次 ID
     * @param evaluationStatus 评估状态
     * @param pageable 分页条件
     * @return 预测明细分页结果
     */
    Page<ModelPredictionDocument> findByBatchIdAndEvaluationStatus(
            String batchId, String evaluationStatus, Pageable pageable);

    /**
     * 查询指定批次全部预测明细，用于能力评估。
     *
     * @param batchId 预测批次 ID
     * @return 预测明细列表
     */
    List<ModelPredictionDocument> findByBatchId(String batchId);

    /**
     * 批量查询多个预测批次的全部明细，用于策略候选样本外评估。
     *
     * @param batchIds 预测批次 ID
     * @return 预测明细
     */
    List<ModelPredictionDocument> findByBatchIdIn(List<String> batchIds);
}
// AI_GENERATE_END ------
