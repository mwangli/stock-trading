// AI_GENERATE_START---
package com.stock.trading.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 后端进程健康状态。
 *
 * @author mwangli
 * @since 2026-10-17
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SystemHealthDto {

    /**
     * 服务状态，后端正常响应时为 UP。
     */
    private String status;

    /**
     * 健康检查对应的服务名称。
     */
    private String service;
}
// AI_GENERATE_END---
