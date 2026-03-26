package com.stock.autoLogin.exception;

/**
 * 登录异常基类
 *
 * @author mwangli
 * @since 2026-03-25
 */
public class LoginException extends RuntimeException {

    public LoginException(String message) {
        super(message);
    }

    public LoginException(String message, Throwable cause) {
        super(message, cause);
    }
}
