// AI_GENERATE_START ---
package com.stock.strategyAgent.api;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 会话响应。
 *
 * @author mwangli
 * @since 2026-10-18
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SessionResponse {
    /** 会话标识。 */
    private String sessionId;
    /** 用户范围。 */
    private String userScope;
    /** 会话标题。 */
    private String title;
    /** 会话状态。 */
    private String status;
    /** 创建时间。 */
    private LocalDateTime createdAt;
    /** 更新时间。 */
    private LocalDateTime updatedAt;
}
// AI_GENERATE_END ---
