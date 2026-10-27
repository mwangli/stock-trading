// AI_GENERATE_START--
package com.stock.platform.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * Agent 使用的真实交易执行门禁摘要。
 *
 * @param targetTradeDate 目标交易日
 * @param tradingDay 是否为交易日
 * @param executableCandidateCount 可执行候选数量
 * @param brokerAuthenticated 券商会话是否有效
 * @param realWriteEnabled 真实写入门禁是否开启
 * @param status 综合状态
 * @param blockedReasons 阻断原因
 * @author mwangli
 * @since 2026-10-23
 */
public record InternalTradeExecutionGateResponse(
        LocalDate targetTradeDate, boolean tradingDay, int executableCandidateCount,
        boolean brokerAuthenticated, boolean realWriteEnabled, String status,
        List<String> blockedReasons) {
}
// AI_GENERATE_END--
