// AI_GENERATE_START ---
package com.stock.strategyAgent.api;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 创建会话请求。
 *
 * @author mwangli
 * @since 2026-10-18
 */
@Data
@NoArgsConstructor
public class CreateSessionRequest {
    /** 用户范围标识。 */
    private String userScope;
    /** 会话标题。 */
    private String title;
}
// AI_GENERATE_END ---
