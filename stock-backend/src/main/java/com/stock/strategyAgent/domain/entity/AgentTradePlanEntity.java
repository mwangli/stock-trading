// AI_GENERATE_START ---
package com.stock.strategyAgent.domain.entity;

import com.stock.strategyAgent.domain.AgentEnums;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalDate;

/**
 * Agent 生成的自动交易计划。
 * 计划只描述候选和执行约束，不保存券商订单号，也不包含人工审批状态。
 *
 * @author mwangli
 * @since 2026-10-17
 */
@Data
@Accessors(chain = true)
@Entity
@Table(name = "agent_trade_plan", indexes = {
        @Index(name = "idx_agent_trade_plan_status_expire", columnList = "status,expires_at"),
        @Index(name = "idx_agent_trade_plan_candidate", columnList = "candidate_batch_id")
})
public class AgentTradePlanEntity {

    /** 交易计划唯一标识。 */
    @Id
    @Column(name = "plan_id", length = 64)
    private String planId;

    /** 候选批次标识。 */
    @Column(name = "candidate_batch_id", nullable = false, length = 128)
    private String candidateBatchId;

    /** 预测批次标识。 */
    @Column(name = "prediction_batch_id", length = 128)
    private String predictionBatchId;

    /** 计划涉及的股票代码。 */
    @Column(name = "stock_code", nullable = false, length = 32)
    private String stockCode;

    /** 交易方向，仅允许 BUY 或 SELL。 */
    @Column(nullable = false, length = 8)
    private String direction;

    /** 计划金额，实际价格和数量由 tradingExecutor 重新计算。 */
    @Column(name = "planned_amount", nullable = false, precision = 18, scale = 4)
    private BigDecimal plannedAmount;

    /** 卖出计划数量，买入计划为空。 */
    @Column(name = "planned_quantity")
    private Integer plannedQuantity;

    /** 证据引用，保存脱敏后的 ID 或版本摘要。 */
    @Column(name = "evidence_refs", nullable = false, columnDefinition = "TEXT")
    private String evidenceRefs;

    /** 计划有效期。 */
    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    /** 防重复执行幂等键。 */
    @Column(name = "idempotency_key", nullable = false, unique = true, length = 128)
    private String idempotencyKey;

    /** 自动交易计划状态。 */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private AgentEnums.TradePlanStatus status;

    /** 创建时间。 */
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    /** 更新时间。 */
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /** 最近一次拒绝或执行摘要。 */
    @Column(name = "decision_message", columnDefinition = "TEXT")
    private String decisionMessage;

    /** 目标交易日。 */
    @Column(name = "target_trade_date")
    private LocalDate targetTradeDate;

    /** 策略配置版本。 */
    @Column(name = "strategy_version", length = 64)
    private String strategyVersion;

    /** 价格约束说明。 */
    @Column(name = "price_constraint", columnDefinition = "TEXT")
    private String priceConstraint;

    /** 计划理由。 */
    @Column(name = "reason", columnDefinition = "TEXT")
    private String reason;

    /** 风险提示 JSON。 */
    @Column(name = "risk_warnings", columnDefinition = "TEXT")
    private String riskWarnings;

    /** 不确定性说明。 */
    @Column(name = "uncertainty", columnDefinition = "TEXT")
    private String uncertainty;
}
// AI_GENERATE_END ---
