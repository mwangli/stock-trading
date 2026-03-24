package com.stock.tradingExecutor.execution;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 登录页四则运算验证码解析（不启动浏览器）。
 *
 * @author mwangli
 * @since 2026-03-22
 */
@ExtendWith(MockitoExtension.class)
class LoginPageHandlerMathCaptchaTest {

    @Mock
    private BrowserSessionManager browserSessionManager;

    @InjectMocks
    private LoginPageHandler loginPageHandler;

    @Test
    @DisplayName("calculateCaptcha：加减乘除")
    void calculateCaptcha_basicOps() {
        assertEquals(5, loginPageHandler.calculateCaptcha("2 + 3 = ?"));
        assertEquals(2, loginPageHandler.calculateCaptcha("5-3="));
        assertEquals(12, loginPageHandler.calculateCaptcha("3*4="));
        assertEquals(4, loginPageHandler.calculateCaptcha("8/2="));
    }

    @Test
    @DisplayName("calculateCaptcha：非法输入返回 -1")
    void calculateCaptcha_invalid() {
        assertEquals(-1, loginPageHandler.calculateCaptcha(null));
        assertEquals(-1, loginPageHandler.calculateCaptcha(""));
        assertEquals(-1, loginPageHandler.calculateCaptcha("no equation"));
    }
}
