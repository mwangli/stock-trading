// AI_GENERATE_START-------
package com.stock.trading.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

/**
 * 当前生效的运行控制配置。
 *
 * @author mwangli
 * @since 2026-10-12
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SystemRuntimeConfigResponse {

    /** 当前配置所属的运行节点标识。 */
    private String nodeId;

    /** 是否在应用启动后执行股票基础数据初始化。 */
    private boolean startupDataSyncEnabled;

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

    /** 配置最后更新时间。 */
    private LocalDateTime updatedAt;
}
// AI_GENERATE_END-------
