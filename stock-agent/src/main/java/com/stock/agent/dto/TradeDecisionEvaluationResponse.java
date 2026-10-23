// AI_GENERATE_START----
package com.stock.agent.dto;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 单个 Agent 交易建议的市场路径与反事实评估响应。
 *
 * @author mwangli
 * @since 2026-10-23
 */
@Value
@Builder
public class TradeDecisionEvaluationResponse {

    /** 评估唯一标识。 */
    String evaluationId;

    /** Agent 判断唯一标识。 */
    String decisionId;

    /** 候选批次标识。 */
    String candidateBatchId;

    /** 股票代码。 */
    String stockCode;

    /** 模型输入基准交易日。 */
    LocalDate baseTradeDate;

    /** 候选目标交易日。 */
    LocalDate targetTradeDate;

    /** 确定性基线是否允许该候选。 */
    Boolean baselineEligible;

    /** Agent 结构化建议。 */
    String agentRecommendation;

    /** 决策基准收盘价，单位为元。 */
    BigDecimal baselineClosePrice;

    /** 目标交易日开盘价，单位为元。 */
    BigDecimal targetOpenPrice;

    /** 目标交易日最高价，单位为元。 */
    BigDecimal targetHighPrice;

    /** 目标交易日最低价，单位为元。 */
    BigDecimal targetLowPrice;

    /** 目标交易日收盘价，单位为元。 */
    BigDecimal targetClosePrice;

    /** 从基准收盘到目标收盘的收益率。 */
    BigDecimal closeReturnRate;

    /** 目标交易日最大有利变动。 */
    BigDecimal maxFavorableRate;

    /** 目标交易日最大不利变动。 */
    BigDecimal maxAdverseRate;

    /** Agent 规避基线买入产生的正收益机会成本。 */
    BigDecimal skipOpportunityCostRate;

    /** Agent 规避基线买入减少的负收益损失。 */
    BigDecimal riskAvoidanceBenefitRate;

    /** 基线与 Agent 建议的反事实关系。 */
    String counterfactualOutcome;

    /** 行情评估状态。 */
    String marketDataStatus;

    /** 关联的真实自动交易批次 ID，可为空。 */
    String executionBatchId;

    /** 真实自动交易批次状态，可为空。 */
    String executionBatchStatus;

    /** 真实自动交易批次已处理候选数量，可为空。 */
    Integer executionProcessedCount;

    /** 真实自动交易批次成功委托数量，可为空。 */
    Integer executionSuccessCount;

    /** 是否能证明当前股票实际成交，Phase 3 固定为 false。 */
    boolean stockExecutionKnown;

    /** 最近评估时间。 */
    LocalDateTime evaluatedAt;
}
// AI_GENERATE_END----
