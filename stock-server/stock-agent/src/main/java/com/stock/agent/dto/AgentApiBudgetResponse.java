// AI_GENERATE_START-------
package com.stock.agent.dto;

import com.stock.agent.client.AgentApiBudgetManager;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

/**
 * Agent API 当日费用预算响应。
 *
 * @author mwangli
 * @since 2026-10-22
 */
@Data
@Builder
public class AgentApiBudgetResponse {

    /** 每日费用门禁是否启用。 */
    private boolean enabled;

    /** 费用归属日期，使用北京时间。 */
    private LocalDate usageDate;

    /** 每日费用上限，单位人民币元。 */
    private BigDecimal dailyLimitCny;

    /** 当日已占用预算，单位人民币元。 */
    private BigDecimal allocatedCostCny;

    /** 当日已按Token统计结算的估算费用，单位人民币元。 */
    private BigDecimal settledCostCny;

    /** 当日剩余可预留预算，单位人民币元。 */
    private BigDecimal remainingCostCny;

    /** 当日已结算输入Token数量。 */
    private long inputTokens;

    /** 当日已结算输出Token数量。 */
    private long outputTokens;

    /** 当日已结算模型请求次数。 */
    private long requestCount;

    /** 当日调用失败或Token统计缺失的保守结算次数。 */
    private long conservativeSettlementCount;

    /**
     * 将内部预算快照转换为接口响应。
     *
     * @param snapshot 内部预算快照
     * @return API费用预算响应
     */
    public static AgentApiBudgetResponse from(AgentApiBudgetManager.BudgetSnapshot snapshot) {
        return AgentApiBudgetResponse.builder()
                .enabled(snapshot.enabled())
                .usageDate(snapshot.usageDate())
                .dailyLimitCny(toCny(snapshot.dailyLimitCostMicros()))
                .allocatedCostCny(toCny(snapshot.allocatedCostMicros()))
                .settledCostCny(toCny(snapshot.settledCostMicros()))
                .remainingCostCny(toCny(snapshot.remainingCostMicros()))
                .inputTokens(snapshot.inputTokens())
                .outputTokens(snapshot.outputTokens())
                .requestCount(snapshot.requestCount())
                .conservativeSettlementCount(snapshot.conservativeSettlementCount())
                .build();
    }

    private static BigDecimal toCny(long micros) {
        return BigDecimal.valueOf(micros)
                .divide(BigDecimal.valueOf(1_000_000L), 6, RoundingMode.HALF_UP);
    }
}
// AI_GENERATE_END------