// AI_GENERATE_START ---
package com.stock.agent.integration.dto;

import java.time.LocalDateTime;

/**
 * Backend 券商状态 HTTP 响应。
 *
 * @param brokerName 券商实现名称
 * @param tradingMode 交易模式
 * @param authenticated 券商会话是否有效
 * @param realWriteEnabled 真实写入总门禁是否启用
 * @param liveWriteAllowed 当前是否允许真实写入
 * @param asOf 事实查询时间
 * @author mwangli
 * @since 2026-10-24
 */
public record BackendBrokerStatusResponse(
        String brokerName, String tradingMode, boolean authenticated,
        boolean realWriteEnabled, boolean liveWriteAllowed, LocalDateTime asOf) {
}
// AI_GENERATE_END --
