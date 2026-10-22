// AI_GENERATE_START---- ---
package com.stock.tradingExecutor.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 系统运行控制配置接口 DTO 集合。
 *
 * @author mwangli
 * @since 2026-10-12
 */
public final class SystemRuntimeConfigDto {

    private SystemRuntimeConfigDto() {
    }

    /**
     * 运行控制配置保存请求。
     *
     * @author mwangli
     * @since 2026-10-12
     */
    @Data
    @NoArgsConstructor
    public static class UpdateRequest {

        /** 是否允许 Quartz 固定任务执行。 */
        private Boolean schedulerEnabled;

        /** 是否在应用启动后执行股票基础数据初始化。 */
        private Boolean startupDataSyncEnabled;

    }

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
    public static class SettingsResponse {

        /** 当前配置所属的运行节点标识。 */
        private String nodeId;

        /** 是否允许 Quartz 固定任务执行。 */
        private boolean schedulerEnabled;

        /** 是否在应用启动后执行股票基础数据初始化。 */
        private boolean startupDataSyncEnabled;

        /** 配置最后更新时间。 */
        private LocalDateTime updatedAt;
    }
}
// AI_GENERATE_END---- ----
