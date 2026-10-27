// AI_GENERATE_START-------
package com.stock.agent.dto;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 交易计划草案与人工审批状态响应。
 *
 * @author mwangli
 * @since 2026-10-23
 */
@Value
@Builder
public class TradePlanResponse {

    /** 计划标识。 */
    String planId;

    /** 候选批次标识。 */
    String candidateBatchId;

    /** 预测批次标识。 */
    String predictionBatchId;

    /** 股票代码。 */
    String stockCode;

    /** 交易方向。 */
    String direction;

    /** 计划金额，单位为元。 */
    BigDecimal plannedAmount;

    /** 卖出计划数量，单位为股。 */
    Integer plannedQuantity;

    /** 脱敏证据引用列表。 */
    List<String> evidenceRefs;

    /** 计划有效期。 */
    LocalDateTime expiresAt;

    /** 用户范围内幂等键。 */
    String idempotencyKey;

    /** 当前计划状态。 */
    String status;

    /** 最近决策或执行摘要。 */
    String decisionMessage;

    /** 最近更新时间。 */
    LocalDateTime updatedAt;

    /** 乐观锁版本。 */
    Long version;

    /** 目标交易日。 */
    LocalDate targetTradeDate;

    /** 策略配置版本。 */
    String modelVersion;

    /** 价格约束说明。 */
    String priceConstraint;

    /** 计划理由。 */
    String reason;

    /** 风险提示列表。 */
    List<String> riskWarnings;

    /** 不确定性说明。 */
    String uncertainty;

    /** 批准人用户范围。 */
    String approvedBy;

    /** 批准时间。 */
    LocalDateTime approvedAt;

    /** 拒绝人用户范围。 */
    String rejectedBy;

    /** 拒绝时间。 */
    LocalDateTime rejectedAt;

    /** 人工审批意见。 */
    String reviewComment;

}
// AI_GENERATE_END-------
