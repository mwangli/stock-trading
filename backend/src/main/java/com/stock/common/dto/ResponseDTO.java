package com.stock.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 统一接口响应封装对象
 *
 * 所有后端 REST 接口必须使用该对象对返回结果进行包装，
 * 通过泛型参数 T 承载实际业务数据，确保前后端约定一致。
 *
 * @param <T> 业务数据类型
 *
 * @author mwangli
 * @since 2026-03-10
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResponseDTO<T> {

    /**
     * 是否执行成功
     */
    private boolean success;

    /**
     * 提示信息（成功说明或错误描述）
     */
    private String message;

    /**
     * 实际业务数据载荷
     */
    private T data;

    /**
     * 构建成功响应
     *
     * @param data 业务数据
     * @param <T>  业务数据类型
     * @return 成功响应的 ResponseDTO
     */
    public static <T> ResponseDTO<T> success(T data) {
        return ResponseDTO.<T>builder()
                .success(true)
                .message("success")
                .data(data)
                .build();
    }

    /**
     * 构建失败响应
     *
     * @param message 错误信息
     * @param <T>     业务数据类型
     * @return 失败响应的 ResponseDTO
     */
    public static <T> ResponseDTO<T> error(String message) {
        return ResponseDTO.<T>builder()
                .success(false)
                .message(message)
                .data(null)
                .build();
    }
}

