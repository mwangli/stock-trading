// AI_GENERATE_START--
package com.stock.execution.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 自动交易紧急停止请求。
 *
 * @author mwangli
 * @since 2026-10-24
 */
@Data
@NoArgsConstructor
public class AutomaticTradingEmergencyStopRequest {

    /** 停止原因，不能为空，最大 500 个字符。 */
    private String reason;
}
// AI_GENERATE_END--
