// AI_GENERATE_START-------
package com.stock.trading.repository;

import com.stock.trading.entity.AutomaticTradeExecutionBatch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

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
// AI_GENERATE_END-------
