// AI_GENERATE_START--------
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

    /** 配置最后更新时间。 */
    private LocalDateTime updatedAt;
}
// AI_GENERATE_END--------
