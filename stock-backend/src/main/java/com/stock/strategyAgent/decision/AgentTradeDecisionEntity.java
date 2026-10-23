// AI_GENERATE_START -
package com.stock.strategyAgent.decision;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/**
 * Agent 单候选结构化交易判断及 SHADOW 融合结果。
 *
 * @author mwangli
 * @since 2026-10-23
 */
@Data
@Accessors(chain = true)
@Entity
@Table(name = "agent_trade_decision", indexes = {
        @Index(name = "idx_agent_trade_decision_batch", columnList = "candidate_batch_id,created_at"),
        @Index(name = "idx_agent_trade_decision_status", columnList = "status,valid_until")
}, uniqueConstraints = @UniqueConstraint(name = "uk_agent_trade_decision_context_version",
        columnNames = {"context_hash", "prompt_version", "decision_mode"}))
public class AgentTradeDecisionEntity {

    /** 决策唯一标识。 */
    @Id
    @Column(name = "decision_id", length = 64)
    private String decisionId;

    /** 关联上下文标识。 */
    @Column(name = "context_id", nullable = false, length = 64)
    private String contextId;

    /** 关联上下文哈希。 */
    @Column(name = "context_hash", nullable = false, length = 64)
    private String contextHash;

    /** 候选批次标识。 */
    @Column(name = "candidate_batch_id", nullable = false, length = 128)
    private String candidateBatchId;

    /** 股票代码。 */
    @Column(name = "stock_code", nullable = false, length = 32)
    private String stockCode;

    /** Agent 建议。 */
    @Column(name = "recommendation", nullable = false, length = 16)
    private String recommendation;

    /** 受控原因码 JSON。 */
    @Lob
    @Column(name = "reason_codes", nullable = false, columnDefinition = "TEXT")
    private String reasonCodes;

    /** 判断摘要。 */
    @Column(name = "summary", nullable = false, length = 500)
    private String summary;

    /** 核心判断说明。 */
    @Lob
    @Column(name = "thesis", columnDefinition = "TEXT")
    private String thesis;

    /** 支持证据引用 JSON。 */
    @Lob
    @Column(name = "supporting_evidence_refs", nullable = false, columnDefinition = "TEXT")
    private String supportingEvidenceRefs;

    /** 反证引用 JSON。 */
    @Lob
    @Column(name = "contrary_evidence_refs", nullable = false, columnDefinition = "TEXT")
    private String contraryEvidenceRefs;

    /** 风险标记 JSON。 */
    @Lob
    @Column(name = "risk_flags", nullable = false, columnDefinition = "TEXT")
    private String riskFlags;

    /** 判断失效条件 JSON。 */
    @Lob
    @Column(name = "invalidation_conditions", nullable = false, columnDefinition = "TEXT")
    private String invalidationConditions;

    /** 建议优先级，范围 0 至 100。 */
    @Column(name = "suggested_priority")
    private Integer suggestedPriority;

    /** 建议仓位下限，小数形式。 */
    @Column(name = "allocation_min")
    private Double allocationMin;

    /** 建议仓位上限，小数形式。 */
    @Column(name = "allocation_max")
    private Double allocationMax;

    /** 模型自评置信区间。 */
    @Column(name = "confidence_band", nullable = false, length = 16)
    private String confidenceBand;

    /** 决策参与模式。 */
    @Column(name = "decision_mode", nullable = false, length = 16)
    private String decisionMode;

    /** 决策状态。 */
    @Column(name = "status", nullable = false, length = 16)
    private String status;

    /** Java 融合结果。 */
    @Column(name = "fusion_outcome", nullable = false, length = 32)
    private String fusionOutcome;

    /** Agent 是否实际影响最终结果。 */
    @Column(name = "impacted", nullable = false)
    private boolean impacted;

    /** 融合说明。 */
    @Column(name = "fusion_reason", nullable = false, length = 500)
    private String fusionReason;

    /** Provider 名称。 */
    @Column(name = "provider", length = 32)
    private String provider;

    /** 模型名称。 */
    @Column(name = "model", length = 128)
    private String model;

    /** 提示词契约版本。 */
    @Column(name = "prompt_version", nullable = false, length = 32)
    private String promptVersion;

    /** 不确定性说明。 */
    @Lob
    @Column(name = "uncertainty", columnDefinition = "TEXT")
    private String uncertainty;

    /** 决策有效期。 */
    @Column(name = "valid_until", nullable = false)
    private LocalDateTime validUntil;

    /** 决策创建时间。 */
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
// AI_GENERATE_END -
