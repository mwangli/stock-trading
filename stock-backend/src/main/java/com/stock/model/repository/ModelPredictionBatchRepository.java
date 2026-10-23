// AI_GENERATE_START----------
package com.stock.model.repository;

import com.stock.model.entity.ModelPredictionBatchDocument;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 模型预测批次仓库。
 *
 * @author mwangli
 * @since 2026-10-12
 */
@Repository
public interface ModelPredictionBatchRepository extends JpaRepository<ModelPredictionBatchDocument, String> {

    /**
     * 查询尚未记录交易日历来源的历史批次。
     *
     * @return 待回填日历来源的批次
     */
    List<ModelPredictionBatchDocument> findByCalendarFallbackIsNull();

    /**
     * 查询未结束预测批次。
     *
     * @param statuses 未结束状态集合
     * @return 未结束批次列表
     */
    List<ModelPredictionBatchDocument> findByStatusIn(Collection<String> statuses);

    /**
     * 判断是否仍有指定状态的预测批次。
     *
     * @param status 预测批次状态
     * @return 是否存在匹配批次
     */
    boolean existsByStatus(String status);

    /**
     * 按目标交易日从旧到新查询指定评估队列状态的批次。
     *
     * @param evaluationQueueStatus 评估队列状态
     * @return 按目标交易日和创建时间升序排列的批次
     */
    List<ModelPredictionBatchDocument> findByEvaluationQueueStatusOrderByTargetTradeDateAscCreatedAtAsc(
            String evaluationQueueStatus);

    /**
     * 判断是否仍有指定状态的评估队列批次。
     *
     * @param evaluationQueueStatus 评估队列状态
     * @return 是否存在匹配批次
     */
    boolean existsByEvaluationQueueStatus(String evaluationQueueStatus);

    /**
     * 按预测队列顺序锁定下一条待执行批次。
     *
     * @param status 预测队列状态
     * @return 已锁定批次
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<ModelPredictionBatchDocument> findFirstByStatusOrderByTargetTradeDateAscCreatedAtAsc(String status);

    /**
     * 按评估队列顺序锁定下一条待执行批次。
     *
     * @param evaluationQueueStatus 评估队列状态
     * @return 已锁定批次
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<ModelPredictionBatchDocument>
            findFirstByEvaluationQueueStatusOrderByTargetTradeDateAscCreatedAtAsc(String evaluationQueueStatus);

    /**
     * 按主键锁定预测批次。
     *
     * @param id 批次主键
     * @return 已锁定批次
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select item from ModelPredictionBatchDocument item where item.id = :id")
    Optional<ModelPredictionBatchDocument> findByIdForUpdate(@Param("id") String id);

    /**
     * 查询可评估但尚未进入终态队列的预测批次。
     *
     * @param statuses 可评估预测状态
     * @param queueStatuses 需排除的评估队列状态
     * @return 候选批次
     */
    @Query("""
            select item from ModelPredictionBatchDocument item
            where item.status in :statuses
              and (item.evaluationQueueStatus is null or item.evaluationQueueStatus not in :queueStatuses)
            order by item.targetTradeDate asc, item.createdAt asc
            """)
    List<ModelPredictionBatchDocument> findEvaluationCandidates(
            @Param("statuses") Collection<String> statuses,
            @Param("queueStatuses") Collection<String> queueStatuses);

    /**
     * 分页查询全部预测批次。
     *
     * @param pageable 分页条件
     * @return 预测批次分页结果
     */
    Page<ModelPredictionBatchDocument> findAll(Pageable pageable);

    /**
     * 查询最新预测批次。
     *
     * @return 最新批次，不存在时返回 null
     */
    ModelPredictionBatchDocument findTopByOrderByCreatedAtDesc();

    /**
     * 查询指定来源批次最近生成的历史重算批次。
     *
     * @param rerunSourceBatchId 原始预测批次 ID
     * @return 历史重算批次，不存在时返回 null
     */
    ModelPredictionBatchDocument findFirstByRerunSourceBatchIdOrderByCreatedAtDesc(String rerunSourceBatchId);

    /**
     * 查询同一模型和基准交易日最近批次，用于幂等控制。
     *
     * @param modelId 模型文档 ID
     * @param baseTradeDate 基准交易日
     * @return 最近批次，不存在时返回 null
     */
    ModelPredictionBatchDocument findFirstByModelIdAndBaseTradeDateOrderByCreatedAtDesc(
            String modelId, LocalDate baseTradeDate);

    /**
     * 查询目标交易日最新可用于策略候选的预测批次。
     *
     * @param targetTradeDate 目标交易日
     * @param statuses 可用批次状态
     * @return 最新匹配批次，不存在时返回 null
     */
    ModelPredictionBatchDocument findFirstByTargetTradeDateAndStatusInOrderByCreatedAtDesc(
            LocalDate targetTradeDate, Collection<String> statuses);

    /**
     * 查询同一模型最近完成的预测批次，用于滚动窗口评估。
     *
     * @param modelId 模型文档 ID
     * @param statuses 可参与滚动统计的批次状态
     * @return 按目标交易日倒序排列的批次
     */
    List<ModelPredictionBatchDocument> findByModelIdAndStatusInOrderByTargetTradeDateDesc(
            String modelId, Collection<String> statuses);

    /**
     * 查询指定模型版本最近一次包含有效样本的评估批次。
     *
     * @param modelId 模型文档 ID
     * @param evaluatedCount 已评估样本数下限
     * @return 最近一次有效评估批次，不存在时返回 null
     */
    ModelPredictionBatchDocument findFirstByModelIdAndEvaluatedCountGreaterThanOrderByEvaluationFinishedAtDescCreatedAtDesc(
            String modelId, int evaluatedCount);
}
// AI_GENERATE_END----------
