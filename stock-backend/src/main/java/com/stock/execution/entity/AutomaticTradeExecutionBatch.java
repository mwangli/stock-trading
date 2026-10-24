// AI_GENERATE_START-------
package com.stock.execution.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

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
@Table(name = "automatic_trade_execution_batch", uniqueConstraints = {
        @UniqueConstraint(name = "uk_auto_trade_batch_id", columnNames = "batch_id"),
        @UniqueConstraint(name = "uk_auto_trade_idempotency", columnNames = "idempotency_key")
})
public class AutomaticTradeExecutionBatch {

    /** 数据库主键。 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 执行批次业务 ID。 */
    private String batchId;

    /** 交易日和执行类型组成的幂等键。 */
    private String idempotencyKey;

    /** 执行类型：BUY 或 EXIT。 */
    private String executionType;

    /** 对应交易日。 */
    private LocalDate tradeDate;

    /** 买入任务使用的候选批次 ID。 */
    private String candidateBatchId;

    /** 执行状态：RUNNING、SUCCESS 或 FAILED。 */
    private String status;

    /** 实际尝试处理数量。 */
    private int processedCount;

    /** 成功委托数量。 */
    private int successCount;

    /** 执行结果或失败摘要。 */
    @Lob
    @Column(columnDefinition = "LONGTEXT")
    private String message;

    /** 执行开始时间。 */
    private LocalDateTime startedAt;

    /** 执行结束时间。 */
    private LocalDateTime finishedAt;

    /** 最近更新时间。 */
    private LocalDateTime updatedAt;

    /** 初始化执行批次更新时间。 */
    @PrePersist
    protected void onCreate() {
        updatedAt = updatedAt == null ? LocalDateTime.now() : updatedAt;
    }

    /** 刷新执行批次更新时间。 */
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
// AI_GENERATE_END-------
