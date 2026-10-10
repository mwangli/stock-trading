// AI_GENERATE_START --
package com.stock.tradingExecutor.domain.dto;

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

    /** 真实写入总门禁是否开启。 */
    private boolean realWriteEnabled;

    /** 当前是否允许真实下单或撤单。 */
    private boolean liveWriteAllowed;
}
// AI_GENERATE_END --
