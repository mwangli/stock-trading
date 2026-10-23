// AI_GENERATE_START----
package com.stock.agent.decision.entity;

import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.index.Indexed;

import org.springframework.data.annotation.Id;

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
@Document(collection = "trade_decision_evaluation")
public class TradeDecisionEvaluationEntity {

    /** 评估唯一标识。 */
    @Id
    private String evaluationId;

    /** Agent 判断唯一标识。 */
    @Indexed(unique = true, name = "uk_trade_decision_evaluation_decision")
    private String decisionId;

    /** 不可变上下文标识。 */
    private String contextId;

    /** 候选批次标识。 */
    private String candidateBatchId;

    /** 股票代码。 */
    private String stockCode;

    /** 模型输入基准交易日。 */
    private LocalDate baseTradeDate;

    /** 候选目标交易日。 */
    private LocalDate targetTradeDate;

    /** 确定性基线是否允许该候选。 */
    private Boolean baselineEligible;

    /** Agent 结构化建议。 */
    private String agentRecommendation;

    /** 决策基准收盘价，单位为元。 */
    private BigDecimal baselineClosePrice;

    /** 目标交易日开盘价，单位为元。 */
    private BigDecimal targetOpenPrice;

    /** 目标交易日最高价，单位为元。 */
    private BigDecimal targetHighPrice;

    /** 目标交易日最低价，单位为元。 */
    private BigDecimal targetLowPrice;

    /** 目标交易日收盘价，单位为元。 */
    private BigDecimal targetClosePrice;

    /** 从基准收盘到目标收盘的收益率，小数形式。 */
    private BigDecimal closeReturnRate;

    /** 目标交易日最大有利变动，小数形式。 */
    private BigDecimal maxFavorableRate;

    /** 目标交易日最大不利变动，小数形式。 */
    private BigDecimal maxAdverseRate;

    /** Agent 若规避基线买入产生的正收益机会成本。 */
    private BigDecimal skipOpportunityCostRate;

    /** Agent 若规避基线买入减少的负收益损失。 */
    private BigDecimal riskAvoidanceBenefitRate;

    /** 基线与 Agent 建议的反事实关系。 */
    private String counterfactualOutcome;

    /** 行情评估状态。 */
    private String marketDataStatus;

    /** 关联的真实自动交易批次 ID，可为空。 */
    private String executionBatchId;

    /** 真实自动交易批次状态，可为空。 */
    private String executionBatchStatus;

    /** 真实自动交易批次已处理候选数量，可为空。 */
    private Integer executionProcessedCount;

    /** 真实自动交易批次成功委托数量，可为空。 */
    private Integer executionSuccessCount;

    /** 是否能证明当前股票实际成交，Phase 3 固定为 false。 */
    private boolean stockExecutionKnown;

    /** 最近评估时间。 */
    private LocalDateTime evaluatedAt;
}
// AI_GENERATE_END----
