// AI_GENERATE_START -
package com.stock.backend.internal.dto;

import java.time.LocalDateTime;

/**
 * Agent 使用的券商只读状态。
 *
 * @param brokerName 券商实现名称
 * @param tradingMode 交易模式
 * @param authenticated 会话是否有效
 * @param realWriteEnabled 真实写入总门禁是否开启
 * @param liveWriteAllowed 当前是否允许真实写入
 * @param asOf 查询时间
 * @author mwangli
 * @since 2026-10-23
 */
public record InternalBrokerStatusResponse(
        String brokerName, String tradingMode, boolean authenticated,
        boolean realWriteEnabled, boolean liveWriteAllowed, LocalDateTime asOf) {
}
// AI_GENERATE_END -
