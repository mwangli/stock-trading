// AI_GENERATE_START ---
package com.stock.strategyAgent.api;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.util.List;

/**
 * 自动交易计划接口 DTO。
 *
 * @author mwangli
 * @since 2026-10-17
 */
public final class TradePlanDto {

    private TradePlanDto() {
    }

    /** 创建交易计划请求。 */
    @Data
    @NoArgsConstructor
    public static class CreateRequest {
        /** 候选批次标识。 */
        private String candidateBatchId;
        /** 预测批次标识。 */
        private String predictionBatchId;
        /** 股票代码。 */
        private String stockCode;
        /** 交易方向。 */
        private String direction;
        /** 计划金额。 */
        private BigDecimal plannedAmount;
        /** 卖出计划数量，买入计划不填写。 */
        private Integer plannedQuantity;
        /** 证据引用列表。 */
        private List<String> evidenceRefs;
        /** 计划有效期。 */
        private LocalDateTime expiresAt;
        /** 幂等键。 */
        private String idempotencyKey;
        /** 目标交易日。 */
        private LocalDate targetTradeDate;
        /** 策略配置版本。 */
        private String strategyVersion;
        /** 价格约束说明。 */
        private String priceConstraint;
        /** 计划理由。 */
        private String reason;
        /** 风险提示。 */
        private List<String> riskWarnings;
        /** 不确定性说明。 */
        private String uncertainty;
    }

    /** 交易计划响应。 */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Response {
        /** 计划标识。 */
        private String planId;
        /** 候选批次标识。 */
        private String candidateBatchId;
        /** 预测批次标识。 */
        private String predictionBatchId;
        /** 股票代码。 */
        private String stockCode;
        /** 交易方向。 */
        private String direction;
        /** 计划金额。 */
        private BigDecimal plannedAmount;
        /** 卖出计划数量。 */
        private Integer plannedQuantity;
        /** 证据引用。 */
        private List<String> evidenceRefs;
        /** 计划有效期。 */
        private LocalDateTime expiresAt;
        /** 幂等键。 */
        private String idempotencyKey;
        /** 计划状态。 */
        private String status;
        /** 决策摘要。 */
        private String decisionMessage;
        /** 更新时间。 */
        private LocalDateTime updatedAt;
        /** 目标交易日。 */
        private LocalDate targetTradeDate;
        /** 策略配置版本。 */
        private String strategyVersion;
        /** 价格约束说明。 */
        private String priceConstraint;
        /** 计划理由。 */
        private String reason;
        /** 风险提示。 */
        private List<String> riskWarnings;
        /** 不确定性说明。 */
        private String uncertainty;
    }
}
// AI_GENERATE_END ---
