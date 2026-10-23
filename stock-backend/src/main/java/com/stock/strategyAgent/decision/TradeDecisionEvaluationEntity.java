// AI_GENERATE_START -
package com.stock.strategyAgent.decision;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Agent 影子交易判断的市场路径与反事实评估结果。
 * 只记录基线和 Agent 判断的比较，不修改真实交易状态。
 *
 * @author mwangli
 * @since 2026-10-23
 */
@Data
@Accessors(chain = true)
@Entity
@Table(name = "trade_decision_evaluation", indexes = {
        @Index(name = "idx_trade_decision_evaluation_batch", columnList = "candidate_batch_id,evaluated_at"),
        @Index(name = "idx_trade_decision_evaluation_status", columnList = "market_data_status,target_trade_date")
}, uniqueConstraints = @UniqueConstraint(name = "uk_trade_decision_evaluation_decision",
        columnNames = "decision_id"))
public class TradeDecisionEvaluationEntity {

    /** 评估唯一标识。 */
    @Id
    @Column(name = "evaluation_id", length = 64)
    private String evaluationId;

    /** Agent 判断唯一标识。 */
    @Column(name = "decision_id", nullable = false, length = 64)
    private String decisionId;

    /** 不可变上下文标识。 */
    @Column(name = "context_id", nullable = false, length = 64)
    private String contextId;

    /** 候选批次标识。 */
    @Column(name = "candidate_batch_id", nullable = false, length = 128)
    private String candidateBatchId;

    /** 股票代码。 */
    @Column(name = "stock_code", nullable = false, length = 32)
    private String stockCode;

    /** 模型输入基准交易日。 */
    @Column(name = "base_trade_date")
    private LocalDate baseTradeDate;

    /** 候选目标交易日。 */
    @Column(name = "target_trade_date")
    private LocalDate targetTradeDate;

    /** 确定性基线是否允许该候选。 */
    @Column(name = "baseline_eligible")
    private Boolean baselineEligible;

    /** Agent 结构化建议。 */
    @Column(name = "agent_recommendation", nullable = false, length = 16)
    private String agentRecommendation;

    /** 决策基准收盘价，单位为元。 */
    @Column(name = "baseline_close_price", precision = 20, scale = 6)
    private BigDecimal baselineClosePrice;

    /** 目标交易日开盘价，单位为元。 */
    @Column(name = "target_open_price", precision = 20, scale = 6)
    private BigDecimal targetOpenPrice;

    /** 目标交易日最高价，单位为元。 */
    @Column(name = "target_high_price", precision = 20, scale = 6)
    private BigDecimal targetHighPrice;

    /** 目标交易日最低价，单位为元。 */
    @Column(name = "target_low_price", precision = 20, scale = 6)
    private BigDecimal targetLowPrice;

    /** 目标交易日收盘价，单位为元。 */
    @Column(name = "target_close_price", precision = 20, scale = 6)
    private BigDecimal targetClosePrice;

    /** 从基准收盘到目标收盘的收益率，小数形式。 */
    @Column(name = "close_return_rate", precision = 16, scale = 8)
    private BigDecimal closeReturnRate;

    /** 目标交易日最大有利变动，小数形式。 */
    @Column(name = "max_favorable_rate", precision = 16, scale = 8)
    private BigDecimal maxFavorableRate;

    /** 目标交易日最大不利变动，小数形式。 */
    @Column(name = "max_adverse_rate", precision = 16, scale = 8)
    private BigDecimal maxAdverseRate;

    /** Agent 若规避基线买入产生的正收益机会成本。 */
    @Column(name = "skip_opportunity_cost_rate", precision = 16, scale = 8)
    private BigDecimal skipOpportunityCostRate;

    /** Agent 若规避基线买入减少的负收益损失。 */
    @Column(name = "risk_avoidance_benefit_rate", precision = 16, scale = 8)
    private BigDecimal riskAvoidanceBenefitRate;

    /** 基线与 Agent 建议的反事实关系。 */
    @Column(name = "counterfactual_outcome", nullable = false, length = 32)
    private String counterfactualOutcome;

    /** 行情评估状态。 */
    @Column(name = "market_data_status", nullable = false, length = 32)
    private String marketDataStatus;

    /** 关联的真实自动交易批次 ID，可为空。 */
    @Column(name = "execution_batch_id", length = 64)
    private String executionBatchId;

    /** 真实自动交易批次状态，可为空。 */
    @Column(name = "execution_batch_status", length = 16)
    private String executionBatchStatus;

    /** 真实自动交易批次已处理候选数量，可为空。 */
    @Column(name = "execution_processed_count")
    private Integer executionProcessedCount;

    /** 真实自动交易批次成功委托数量，可为空。 */
    @Column(name = "execution_success_count")
    private Integer executionSuccessCount;

    /** 是否能证明当前股票实际成交，Phase 3 固定为 false。 */
    @Column(name = "stock_execution_known", nullable = false)
    private boolean stockExecutionKnown;

    /** 最近评估时间。 */
    @Column(name = "evaluated_at", nullable = false)
    private LocalDateTime evaluatedAt;
}
// AI_GENERATE_END -
