// AI_GENERATE_START -
package com.stock.trading.dto;

/**
 * Agent 交易门禁响应数据。
 *
 * @param allowed 是否允许继续进入交易复核链
 * @param outcome 门禁结果代码
 * @param reason 结果说明
 * @author mwangli
 * @since 2026-10-23
 */
public record AgentTradeDecisionGateResponse(boolean allowed, String outcome, String reason) {
}
// AI_GENERATE_END -
