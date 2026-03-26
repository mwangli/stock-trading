package com.stock.autoLogin.exception;

/**
 * 验证码异常
 *
 * @author mwangli
 * @since 2026-03-25
 */
public class CaptchaException extends LoginException {

    public CaptchaException(String message) {
        super("验证码处理失败：" + message);
    }

    public CaptchaException(String message, Throwable cause) {
        super("验证码处理失败：" + message, cause);
    }
}
