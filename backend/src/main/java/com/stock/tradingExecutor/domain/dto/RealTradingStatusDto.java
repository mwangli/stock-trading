// AI_GENERATE_START -
package com.stock.tradingExecutor.domain.dto;

import com.stock.tradingExecutor.execution.TradingMode;
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

    /** 当前交易模式。 */
    private TradingMode mode;

    /** 真实交易写入总门禁是否开启。 */
    private boolean realWriteEnabled;

    /** 券商会话是否有效。 */
    private boolean brokerAuthenticated;

    /** 是否允许无人值守执行。 */
    private boolean automaticExecutionEnabled;
}
// AI_GENERATE_END -
