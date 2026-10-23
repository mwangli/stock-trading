// AI_GENERATE_START-----
package com.stock.agent.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Memory 纠错请求。
 *
 * @author mwangli
 * @since 2026-10-18
 */
@Data
@NoArgsConstructor
public class CorrectMemoryRequest {
    /** 修正后的记忆内容。 */
    private String content;
    /** 修正来源。 */
    private String sourceRef;
}
// AI_GENERATE_END-----
