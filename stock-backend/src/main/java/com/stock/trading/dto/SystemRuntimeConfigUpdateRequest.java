// AI_GENERATE_START---
package com.stock.trading.dto;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 运行控制配置保存请求。
 *
 * @author mwangli
 * @since 2026-10-12
 */
@Data
@NoArgsConstructor
public class SystemRuntimeConfigUpdateRequest {

    /** 是否允许 Quartz 固定任务执行。 */
    private Boolean schedulerEnabled;

    /** 是否在应用启动后执行股票基础数据初始化。 */
    private Boolean startupDataSyncEnabled;

}
// AI_GENERATE_END---
