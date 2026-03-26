package com.stock.autoLogin.exception;

/**
 * 浏览器异常
 *
 * @author mwangli
 * @since 2026-03-25
 */
public class BrowserException extends LoginException {

    public BrowserException(String message) {
        super("浏览器操作失败：" + message);
    }

    public BrowserException(String message, Throwable cause) {
        super("浏览器操作失败：" + message, cause);
    }
}
