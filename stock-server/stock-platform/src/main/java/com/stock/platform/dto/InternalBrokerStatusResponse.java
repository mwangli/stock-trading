// AI_GENERATE_START---
package com.stock.platform.dto;

import java.time.LocalDateTime;

/**
 * Agent 使用的券商只读状态。
 *
 * @param brokerName 券商实现名称
 * @param tradingMode 交易模式
 * @param authenticated 会话是否有效
 * @param asOf 查询时间
 * @author mwangli
 * @since 2026-10-23
 */
public record InternalBrokerStatusResponse(
        String brokerName, String tradingMode, boolean authenticated, LocalDateTime asOf) {
}
// AI_GENERATE_END---
