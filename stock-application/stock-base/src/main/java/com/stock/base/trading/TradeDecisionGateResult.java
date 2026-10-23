// AI_GENERATE_START--
package com.stock.base.trading;

/**
 * 自动交易候选门禁结果。
 *
 * @param allowed 是否允许继续进入交易复核链
 * @param outcome 门禁结果代码
 * @param reason 结果说明
 * @author mwangli
 * @since 2026-10-23
 */
public record TradeDecisionGateResult(boolean allowed, String outcome, String reason) {

    /**
     * 创建不改变 Java 基线的默认结果。
     *
     * @return 基线放行结果
     */
    public static TradeDecisionGateResult baselineAllowed() {
        return new TradeDecisionGateResult(true, "BASELINE_APPROVED", "Agent 门禁未启用，采用 Java 基线");
    }
}
// AI_GENERATE_END--
