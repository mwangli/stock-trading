// AI_GENERATE_START-----
package com.stock.agent.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Agent Provider 运行时模式响应。
 *
 * @author mwangli
 * @since 2026-10-18
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProviderModeResponse {

    /** 当前 Provider 模式。 */
    private String provider;
}
// AI_GENERATE_END----