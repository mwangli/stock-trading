// AI_GENERATE_START -
package com.stock.tradingExecutor.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 自动真实交易执行批次。
 * 用于按交易日阻止自动买入或退出任务被重复执行。
 *
 * @author mwangli
 * @since 2026-10-14
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "automatic_trade_execution_batch")
public class AutomaticTradeExecutionBatch {

    /** 数据库主键。 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 执行批次业务 ID。 */
    @Column(name = "batch_id", nullable = false, unique = true, length = 64)
    private String batchId;

    /** 交易日和执行类型组成的幂等键。 */
    @Column(name = "idempotency_key", nullable = false, unique = true, length = 96)
    private String idempotencyKey;

    /** 执行类型：BUY 或 EXIT。 */
    @Column(name = "execution_type", nullable = false, length = 16)
    private String executionType;

    /** 对应交易日。 */
    @Column(name = "trade_date", nullable = false)
    private LocalDate tradeDate;

    /** 买入任务使用的候选批次 ID。 */
    @Column(name = "candidate_batch_id", length = 64)
    private String candidateBatchId;

    /** 执行状态：RUNNING、SUCCESS 或 FAILED。 */
    @Column(nullable = false, length = 16)
    private String status;

    /** 实际尝试处理数量。 */
    @Column(name = "processed_count", nullable = false)
    private int processedCount;

    /** 成功委托数量。 */
    @Column(name = "success_count", nullable = false)
    private int successCount;

    /** 执行结果或失败摘要。 */
    @Column(length = 500)
    private String message;

    /** 执行开始时间。 */
    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    /** 执行结束时间。 */
    @Column(name = "finished_at")
    private LocalDateTime finishedAt;

    /** 最近更新时间。 */
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
// AI_GENERATE_END -
