// AI_GENERATE_START --
package com.stock.strategyAgent.tool;

import lombok.Builder;
import lombok.Value;

/**
 * Agent 工具不可变定义。
 *
 * @author mwangli
 * @since 2026-10-17
 */
@Value
@Builder
public class ToolDefinition {

    /** 工具名称。 */
    String name;

    /** 工具版本。 */
    String version;

    /** 工具说明。 */
    String description;

    /** 工具权限。 */
    ToolPermission requiredPermission;

    /** 是否只读。 */
    boolean readOnly;

    /** 结果最大字符数。 */
    int maxResultSize;

    /** 调用超时时间，单位毫秒。 */
    long timeoutMs;
}
// AI_GENERATE_END --
