// AI_GENERATE_START-----
package com.stock.trading.service;

import com.stock.trading.entity.AutomaticTradeExecutionBatch;
import com.stock.trading.repository.AutomaticTradeExecutionBatchRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 自动真实交易执行批次服务。
 *
 * @author mwangli
 * @since 2026-10-14
 */
@Service
@RequiredArgsConstructor
public class AutomaticTradeExecutionBatchService {

    private final AutomaticTradeExecutionBatchRepository repository;

    /**
     * 创建每日唯一执行批次。
     *
     * @param executionType 执行类型
     * @param tradeDate 交易日
     * @param candidateBatchId 候选批次 ID，退出任务可为空
     * @return 新建的执行批次
     * @throws IllegalStateException 当天同类型批次已经存在时抛出
     */
    public synchronized AutomaticTradeExecutionBatch begin(
            String executionType, LocalDate tradeDate, String candidateBatchId) {
        String idempotencyKey = executionType + ":" + tradeDate;
        AutomaticTradeExecutionBatch existing = repository.findByIdempotencyKey(idempotencyKey).orElse(null);
        if (existing != null) {
            throw new IllegalStateException("当天同类型自动交易任务已执行或正在执行: batchId="
                    + existing.getBatchId() + ", status=" + existing.getStatus());
        }
        String batchId = UUID.randomUUID().toString();
        LocalDateTime now = LocalDateTime.now();
        AutomaticTradeExecutionBatch batch = new AutomaticTradeExecutionBatch();
        batch.setBatchId(batchId);
        batch.setIdempotencyKey(idempotencyKey);
        batch.setExecutionType(executionType);
        batch.setTradeDate(tradeDate);
        batch.setCandidateBatchId(candidateBatchId);
        batch.setStatus("RUNNING");
        batch.setProcessedCount(0);
        batch.setSuccessCount(0);
        batch.setStartedAt(now);
        batch.setUpdatedAt(now);
        return repository.save(batch);
    }

    /**
     * 标记执行批次成功。
     *
     * @param batchId 执行批次 ID
     * @param processedCount 已处理数量
     * @param successCount 成功数量
     * @param message 结果摘要
     */
    public void complete(String batchId, int processedCount, int successCount, String message) {
        AutomaticTradeExecutionBatch batch = requireBatch(batchId);
        batch.setStatus("SUCCESS");
        batch.setProcessedCount(processedCount);
        batch.setSuccessCount(successCount);
        batch.setMessage(message);
        batch.setFinishedAt(LocalDateTime.now());
        batch.setUpdatedAt(LocalDateTime.now());
        repository.save(batch);
    }

    /**
     * 标记执行批次失败。
     *
     * @param batchId 执行批次 ID
     * @param message 失败摘要
     */
    public void fail(String batchId, String message) {
        AutomaticTradeExecutionBatch batch = requireBatch(batchId);
        batch.setStatus("FAILED");
        batch.setMessage(limitMessage(message));
        batch.setFinishedAt(LocalDateTime.now());
        batch.setUpdatedAt(LocalDateTime.now());
        repository.save(batch);
    }

    private AutomaticTradeExecutionBatch requireBatch(String batchId) {
        return repository.findByBatchId(batchId)
                .orElseThrow(() -> new IllegalStateException("自动交易执行批次不存在: " + batchId));
    }

    private String limitMessage(String message) {
        if (message == null || message.length() <= 500) {
            return message;
        }
        return message.substring(0, 500);
    }
}
// AI_GENERATE_END-----
