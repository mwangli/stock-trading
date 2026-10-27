// AI_GENERATE_START--------
package com.stock.agent.dto;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Memory 响应。
 *
 * @author mwangli
 * @since 2026-10-18
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MemoryResponse {
    /** 记忆标识。 */
    private String memoryId;
    /** 记忆类型。 */
    private String memoryType;
    /** 来源类型。 */
    private String sourceType;
    /** 记忆内容。 */
    private String content;
    /** 来源引用。 */
    private String sourceRef;
    /** 置信度。 */
    private Double confidence;
    /** 有效期。 */
    private LocalDateTime expiresAt;
    /** 记忆状态。 */
    private String status;
    /** 更新时间。 */
    private LocalDateTime updatedAt;
}
// AI_GENERATE_END-------