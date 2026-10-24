// AI_GENERATE_START ---
package com.stock.agent.integration.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * Backend 交易执行门禁 HTTP 响应。
 *
 * @param targetTradeDate 目标交易日
 * @param tradingDay 是否为交易日
 * @param executableCandidateCount 可执行候选数量
 * @param brokerAuthenticated 券商会话是否有效
 * @param realWriteEnabled 真实写入门禁是否启用
 * @param status 综合状态
 * @param blockedReasons 阻断原因
 * @author mwangli
 * @since 2026-10-24
 */
public record BackendTradeExecutionGateResponse(
        LocalDate targetTradeDate, boolean tradingDay, int executableCandidateCount,
        boolean brokerAuthenticated, boolean realWriteEnabled, String status,
        List<String> blockedReasons) {
}
// AI_GENERATE_END --
