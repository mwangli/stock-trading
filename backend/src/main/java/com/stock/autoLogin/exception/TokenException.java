package com.stock.autoLogin.exception;

/**
 * Token 异常
 *
 * @author mwangli
 * @since 2026-03-25
 */
public class TokenException extends LoginException {

    public TokenException(String message) {
        super("Token 处理失败：" + message);
    }

    public TokenException(String message, Throwable cause) {
        super("Token 处理失败：" + message, cause);
    }
}
