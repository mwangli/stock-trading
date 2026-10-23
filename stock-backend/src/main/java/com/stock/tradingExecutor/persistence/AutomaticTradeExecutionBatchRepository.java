// AI_GENERATE_START --
package com.stock.tradingExecutor.persistence;

import com.stock.tradingExecutor.domain.entity.AutomaticTradeExecutionBatch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * 自动真实交易执行批次仓储。
 *
 * @author mwangli
 * @since 2026-10-14
 */
@Repository
public interface AutomaticTradeExecutionBatchRepository
        extends JpaRepository<AutomaticTradeExecutionBatch, Long> {

    /**
     * 原子创建每日执行批次，重复幂等键不会新增记录。
     *
     * @param batchId 批次业务 ID
     * @param idempotencyKey 幂等键
     * @param executionType 执行类型
     * @param tradeDate 交易日
     * @param candidateBatchId 候选批次 ID
     * @param startedAt 开始时间
     * @return 1 表示创建成功，0 表示已存在
     */
    @Modifying
    @Query(value = """
            INSERT IGNORE INTO automatic_trade_execution_batch (
                batch_id, idempotency_key, execution_type, trade_date, candidate_batch_id,
                status, processed_count, success_count, started_at, updated_at
            ) VALUES (
                :batchId, :idempotencyKey, :executionType, :tradeDate, :candidateBatchId,
                'RUNNING', 0, 0, :startedAt, :startedAt
            )
            """, nativeQuery = true)
    int insertIfAbsent(
            @Param("batchId") String batchId,
            @Param("idempotencyKey") String idempotencyKey,
            @Param("executionType") String executionType,
            @Param("tradeDate") LocalDate tradeDate,
            @Param("candidateBatchId") String candidateBatchId,
            @Param("startedAt") LocalDateTime startedAt);

    /**
     * 按幂等键查询执行批次。
     *
     * @param idempotencyKey 幂等键
     * @return 执行批次
     */
    Optional<AutomaticTradeExecutionBatch> findByIdempotencyKey(String idempotencyKey);

    /**
     * 按业务批次 ID 查询执行批次。
     *
     * @param batchId 批次业务 ID
     * @return 执行批次
     */
    Optional<AutomaticTradeExecutionBatch> findByBatchId(String batchId);

    /**
     * 查询候选批次最近一次真实自动交易执行摘要。
     *
     * @param candidateBatchId 候选批次 ID
     * @return 最近执行批次
     */
    Optional<AutomaticTradeExecutionBatch> findFirstByCandidateBatchIdOrderByStartedAtDesc(
            String candidateBatchId);
}
// AI_GENERATE_END --
