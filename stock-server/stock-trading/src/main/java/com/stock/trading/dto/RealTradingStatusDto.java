// AI_GENERATE_START--------
package com.stock.trading.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 真实交易运行状态。
 *
 * @author mwangli
 * @since 2026-09-25
 */
@Data
@Builder
public class RealTradingStatusDto {

    /** 真实交易写入总门禁是否开启。 */
    private boolean realWriteEnabled;

    /** 券商会话是否有效。 */
    private boolean brokerAuthenticated;

    /** 是否允许无人值守执行。 */
    private boolean automaticExecutionEnabled;

    /** 是否已紧急停止后续自动交易。 */
    private boolean automaticTradingPaused;

    /** 最近一次紧急停止原因。 */
    private String emergencyStopReason;

    /** 最近一次紧急停止时间。 */
    private LocalDateTime emergencyStoppedAt;

    /** 最近一次紧急停止操作者。 */
    private String emergencyStopOperator;

    /** 紧急停止时生效的策略版本。 */
    private String emergencyStopStrategyVersion;

    /** 最近一次紧急停止执行结果。 */
    private String emergencyStopResult;
}
// AI_GENERATE_END--------
