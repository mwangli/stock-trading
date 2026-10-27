// AI_GENERATE_START-----
package com.stock.agent.dto;

import lombok.Data;

/**
 * Agent Provider 运行时切换请求。
 *
 * @author mwangli
 * @since 2026-10-18
 */
@Data
public class ProviderModeRequest {

    /** Provider 模式，支持 auto、deepseek、ollama 和 disabled。 */
    private String provider;
}
// AI_GENERATE_END----