package com.stock.autoLogin.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 登录结果 DTO
 *
 * @author mwangli
 * @since 2026-03-25
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoginResult {

    /**
     * 是否成功
     */
    private boolean success;

    /**
     * Token（登录成功后返回）
     */
    private String token;

    /**
     * 消息
     */
    private String message;

    /**
     * 登录耗时（毫秒）
     */
    private long durationMs;

    /**
     * 创建成功结果
     */
    public static LoginResult success(String token) {
        return new LoginResult(true, token, "登录成功", 0);
    }

    /**
     * 创建成功结果（带耗时）
     */
    public static LoginResult success(String token, long durationMs) {
        return new LoginResult(true, token, "登录成功", durationMs);
    }

    /**
     * 创建失败结果
     */
    public static LoginResult failure(String message) {
        return new LoginResult(false, null, message, 0);
    }
}
