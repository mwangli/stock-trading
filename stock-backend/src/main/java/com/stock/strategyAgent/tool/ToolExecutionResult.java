// AI_GENERATE_START --
package com.stock.strategyAgent.tool;

import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;

/**
 * 工具统一执行结果。
 *
 * @param <T> 工具数据类型
 * @author mwangli
 * @since 2026-10-17
 */
@Value
@Builder
public class ToolExecutionResult<T> {

    /** 是否成功。 */
    boolean success;

    /** 结果状态。 */
    String status;

    /** 工具名称。 */
    String source;

    /** 事实时间。 */
    LocalDateTime asOf;

    /** 工具数据。 */
    T data;

    /** 脱敏后的警告。 */
    String warnings;

    /** 归一化错误码。 */
    String errorCode;
}
// AI_GENERATE_END --
