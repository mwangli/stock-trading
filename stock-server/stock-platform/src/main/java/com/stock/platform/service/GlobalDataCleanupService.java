// AI_GENERATE_START--
package com.stock.platform.service;

import com.stock.data.service.MarketDataCleanupService;
import com.stock.model.service.ModelDataCleanupService;
import com.stock.platform.entity.JobExecutionRecord;
import com.stock.platform.enums.JobExecutionStatus;
import com.stock.platform.repository.JobExecutionRecordRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 全局业务数据清理服务。
 * 按固定保留期清理无引用快照、历史模型制品、新闻派生数据、预测结果和任务审计记录，
 * 每个清理动作独立执行并汇总失败，避免单个数据域异常阻断后续清理。
 *
 * @author mwangli
 * @since 2026-10-25
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GlobalDataCleanupService {

    private static final int JOB_EXECUTION_RETENTION_DAYS = 180;
    private static final List<JobExecutionStatus> JOB_TERMINAL_STATUSES = List.of(
            JobExecutionStatus.SUCCESS,
            JobExecutionStatus.FAILED,
            JobExecutionStatus.SKIPPED,
            JobExecutionStatus.REJECTED,
            JobExecutionStatus.CANCELLED);

    private final MarketDataCleanupService marketDataCleanupService;
    private final ModelDataCleanupService modelDataCleanupService;
    private final JobExecutionRecordRepository jobExecutionRecordRepository;

    /**
     * 执行全部业务数据清理动作。
     *
     * 所有动作都会尝试执行；存在任一失败时，在全部动作结束后抛出汇总异常，
     * 使 Quartz 执行记录保持失败事实，同时不影响其他清理动作。
     */
    public void cleanupAll() {
        Map<String, CleanupAction> actions = new LinkedHashMap<>();
        actions.put("历史终态训练运行", modelDataCleanupService::cleanupTrainingRuns);
        actions.put("历史失败训练快照", modelDataCleanupService::cleanupFailedSnapshots);
        actions.put("无引用 READY 训练快照", modelDataCleanupService::cleanupReadySnapshots);
        actions.put("RETIRED 模型参数", modelDataCleanupService::cleanupRetiredModelArtifacts);
        actions.put("孤儿模型参数", modelDataCleanupService::cleanupOrphanModelArtifacts);
        actions.put("旧版 Mongo LSTM 模型", modelDataCleanupService::cleanupLegacyMongoModels);
        actions.put("过期原始新闻", marketDataCleanupService::cleanupExpiredNews);
        actions.put("过期新闻事件事实", marketDataCleanupService::cleanupExpiredNewsEventFacts);
        actions.put("过期新闻特征快照", modelDataCleanupService::cleanupExpiredNewsFeatureSnapshots);
        actions.put("过期情感影子预测", modelDataCleanupService::cleanupExpiredSentimentShadowPredictions);
        actions.put("过期时序模型影子预测", modelDataCleanupService::cleanupExpiredTemporalShadowPredictions);
        actions.put("过期模型预测批次", modelDataCleanupService::cleanupPredictionBatches);
        actions.put("历史任务执行记录", this::cleanupJobExecutionRecords);
        actions.put("历史模型操作审计", modelDataCleanupService::cleanupModelOperationAudits);

        int succeeded = 0;
        List<String> failures = new ArrayList<>();
        long startedAt = System.nanoTime();
        for (Map.Entry<String, CleanupAction> entry : actions.entrySet()) {
            long actionStartedAt = System.nanoTime();
            try {
                long deleted = entry.getValue().execute();
                succeeded++;
                log.info("[全局数据清理] 动作完成: action={}, deleted={}, costMs={}",
                        entry.getKey(), deleted, elapsedMillis(actionStartedAt));
            } catch (Exception exception) {
                failures.add(entry.getKey() + ": " + exception.getClass().getSimpleName());
                log.error("[全局数据清理] 动作失败，继续执行后续动作: action={}, costMs={}",
                        entry.getKey(), elapsedMillis(actionStartedAt), exception);
            }
        }
        log.info("[全局数据清理] 全部动作结束: succeeded={}, failed={}, costMs={}",
                succeeded, failures.size(), elapsedMillis(startedAt));
        if (!failures.isEmpty()) {
            throw new IllegalStateException("部分业务数据清理失败: " + String.join(", ", failures));
        }
    }

    private long cleanupJobExecutionRecords() {
        List<JobExecutionRecord> records = jobExecutionRecordRepository.findByStatusInAndCreatedAtBefore(
                JOB_TERMINAL_STATUSES, LocalDateTime.now().minusDays(JOB_EXECUTION_RETENTION_DAYS));
        jobExecutionRecordRepository.deleteAllInBatch(records);
        return records.size();
    }

    private long elapsedMillis(long startedAt) {
        return Duration.ofNanos(System.nanoTime() - startedAt).toMillis();
    }

    @FunctionalInterface
    private interface CleanupAction {

        long execute();
    }
}
// AI_GENERATE_END--
