// AI_GENERATE_START ----
package com.stock.agent.model;

import java.time.LocalDateTime;

/**
 * Backend 返回的券商状态事实。
 *
 * @param brokerName 券商实现名称
 * @param tradingMode 交易模式
 * @param authenticated 会话是否有效
 * @param asOf 查询时间
 * @author mwangli
 * @since 2026-10-23
 */
public record BrokerStatusFact(
        String brokerName, String tradingMode, boolean authenticated, LocalDateTime asOf) {
}
// AI_GENERATE_END ---
