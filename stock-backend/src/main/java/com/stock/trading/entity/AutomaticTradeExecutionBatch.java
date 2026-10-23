// AI_GENERATE_START----
package com.stock.trading.entity;

import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.index.Indexed;

import org.springframework.data.annotation.Id;

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
@Document(collection = "automatic_trade_execution_batch")
public class AutomaticTradeExecutionBatch {

    /** 数据库主键。 */
    @Id
    private Long id;

    /** 执行批次业务 ID。 */
    @Indexed(unique = true, name = "uk_auto_trade_batch_id")
    private String batchId;

    /** 交易日和执行类型组成的幂等键。 */
    @Indexed(unique = true, name = "uk_auto_trade_idempotency")
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
    private String message;

    /** 执行开始时间。 */
    private LocalDateTime startedAt;

    /** 执行结束时间。 */
    private LocalDateTime finishedAt;

    /** 最近更新时间。 */
    private LocalDateTime updatedAt;
}
// AI_GENERATE_END----
