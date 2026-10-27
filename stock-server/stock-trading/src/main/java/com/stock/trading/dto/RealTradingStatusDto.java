// AI_GENERATE_START----------
package com.stock.trading.dto;

import lombok.Builder;
import lombok.Data;

/**
 * 真实交易运行状态。
 *
 * @author mwangli
 * @since 2026-09-25
 */
@Data
@Builder
public class RealTradingStatusDto {

    /** 券商会话是否有效。 */
    private boolean brokerAuthenticated;

}
// AI_GENERATE_END----------
