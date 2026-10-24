// AI_GENERATE_START --
package com.stock.agent.shared.api;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Agent 服务统一响应对象。
 *
 * @param <T> 业务数据类型
 * @author mwangli
 * @since 2026-10-23
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResponseDTO<T> {

    /** 是否成功。 */
    private boolean success;

    /** 响应说明。 */
    private String message;

    /** 业务数据。 */
    private T data;

    /**
     * 创建成功响应。
     *
     * @param data 业务数据
     * @param <T> 业务数据类型
     * @return 成功响应
     */
    public static <T> ResponseDTO<T> success(T data) {
        return ResponseDTO.<T>builder().success(true).message("success").data(data).build();
    }

    /**
     * 创建失败响应。
     *
     * @param message 错误说明
     * @param <T> 业务数据类型
     * @return 失败响应
     */
    public static <T> ResponseDTO<T> error(String message) {
        return ResponseDTO.<T>builder().success(false).message(message).build();
    }
}
// AI_GENERATE_END -
