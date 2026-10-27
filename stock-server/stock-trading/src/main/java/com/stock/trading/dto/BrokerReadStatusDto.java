// AI_GENERATE_START-------
package com.stock.trading.dto;

import lombok.Builder;
import lombok.Data;

/**
 * 券商只读网关状态。
 *
 * @author mwangli
 * @since 2026-09-25
 */
@Data
@Builder
public class BrokerReadStatusDto {

    /** 当前券商实现名称。 */
    private String brokerName;

    /** 当前交易模式。 */
    private String tradingMode;

    /** 当前券商会话是否有效。 */
    private boolean authenticated;

}
// AI_GENERATE_END-------
