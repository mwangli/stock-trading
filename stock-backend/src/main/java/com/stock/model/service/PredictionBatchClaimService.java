// AI_GENERATE_START--
package com.stock.model.service;

import com.stock.model.entity.ModelPredictionBatchDocument;
import com.stock.model.repository.ModelPredictionBatchRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 预测与评估批次的事务认领服务。
 * 使用 MySQL 悲观锁保证多节点只认领同一个批次一次。
 *
 * @author mwangli
 * @since 2026-10-23
 */
@Service
@RequiredArgsConstructor
public class PredictionBatchClaimService {

    private final ModelPredictionBatchRepository batchRepository;

    /**
     * 认领下一条排队中的预测批次。
     *
     * @return 已切换为运行状态的批次，不存在时返回 null
     */
    @Transactional
    public ModelPredictionBatchDocument claimNextPrediction() {
        ModelPredictionBatchDocument batch = batchRepository
                .findFirstByStatusOrderByTargetTradeDateAscCreatedAtAsc("QUEUED")
                .orElse(null);
        if (batch == null) {
            return null;
        }
        LocalDateTime now = LocalDateTime.now();
        batch.setStatus("RUNNING");
        batch.setStartedAt(now);
        batch.setProgressMessage("正在执行全市场预测");
        batch.setUpdatedAt(now);
        return batchRepository.saveAndFlush(batch);
    }

    /**
     * 将满足业务条件的批次原子切换为评估排队状态。
     *
     * @param batchId 预测批次主键
     * @return 是否成功进入评估队列
     */
    @Transactional
    public boolean enqueueEvaluation(String batchId) {
        ModelPredictionBatchDocument batch = batchRepository.findByIdForUpdate(batchId).orElse(null);
        if (batch == null || !java.util.Set.of("COMPLETED", "PARTIAL", "EVALUATED").contains(batch.getStatus())
                || "QUEUED".equals(batch.getEvaluationQueueStatus())
                || "RUNNING".equals(batch.getEvaluationQueueStatus())) {
            return false;
        }
        LocalDateTime now = LocalDateTime.now();
        batch.setEvaluationQueueStatus("QUEUED");
        batch.setEvaluationWaitReason(null);
        batch.setEvaluationQueuedAt(now);
        batch.setEvaluationStartedAt(null);
        batch.setEvaluationFinishedAt(null);
        batch.setEvaluationErrorMessage(null);
        batch.setUpdatedAt(now);
        batchRepository.saveAndFlush(batch);
        return true;
    }

    /**
     * 认领下一条排队中的评估批次。
     *
     * @return 已切换为运行状态的批次，不存在时返回 null
     */
    @Transactional
    public ModelPredictionBatchDocument claimNextEvaluation() {
        ModelPredictionBatchDocument batch = batchRepository
                .findFirstByEvaluationQueueStatusOrderByTargetTradeDateAscCreatedAtAsc("QUEUED")
                .orElse(null);
        if (batch == null) {
            return null;
        }
        LocalDateTime now = LocalDateTime.now();
        batch.setEvaluationQueueStatus("RUNNING");
        batch.setEvaluationWaitReason(null);
        batch.setEvaluationStartedAt(now);
        batch.setEvaluationErrorMessage(null);
        batch.setUpdatedAt(now);
        return batchRepository.saveAndFlush(batch);
    }
}
// AI_GENERATE_END--
