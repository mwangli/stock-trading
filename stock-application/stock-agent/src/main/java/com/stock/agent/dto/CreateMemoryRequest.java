// AI_GENERATE_START-------
package com.stock.agent.dto;

import com.stock.agent.domain.AgentEnums;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Memory 创建请求。
 *
 * @author mwangli
 * @since 2026-10-18
 */
@Data
@NoArgsConstructor
public class CreateMemoryRequest {
    /** 记忆类型。 */
    private AgentEnums.MemoryType memoryType;
    /** 来源类型。 */
    private AgentEnums.MemorySourceType sourceType;
    /** 记忆内容。 */
    private String content;
    /** 来源引用。 */
    private String sourceRef;
    /** 置信度。 */
    private Double confidence;
    /** 有效天数。 */
    private Integer ttlDays;
}
// AI_GENERATE_END-------
