package com.stock.autoLogin.service;

import com.stock.autoLogin.exception.TokenException;
import com.stock.tradingExecutor.execution.ZXRequestUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.Cookie;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.springframework.stereotype.Service;

import java.util.Set;

/**
 * Token 管理器
 * 负责从 Cookie、localStorage、sessionStorage 中提取 Token，并同步到交易工具
 *
 * @author mwangli
 * @since 2026-03-25
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CookieManager {

    private final ZXRequestUtils zxRequestUtils;

    /**
     * 从 Cookie / localStorage / sessionStorage 提取 Token
     * 多策略依次尝试
     *
     * @param driver WebDriver 实例
     * @return Token 字符串，未找到返回 null
     */
    public String extractToken(WebDriver driver) {
        if (driver == null) {
            throw new TokenException("WebDriver 未初始化");
        }

        JavascriptExecutor js = (JavascriptExecutor) driver;

        // 策略 1: Cookie 中提取
        try {
            Set<Cookie> cookies = driver.manage().getCookies();
            for (Cookie cookie : cookies) {
                if (cookie.getName().equalsIgnoreCase("token") ||
                        cookie.getName().equalsIgnoreCase("auth_token") ||
                        cookie.getName().equalsIgnoreCase("session_id")) {
                    String value = cookie.getValue();
                    log.info("从 Cookie 提取 Token: {}***",
                            value.substring(0, Math.min(10, value.length())));
                    return value;
                }
            }
        } catch (Exception e) {
            log.warn("从 Cookie 提取 Token 失败: {}", e.getMessage());
        }

        // 策略 2: localStorage 中提取
        try {
            String localStorageKeys = (String) js.executeScript(
                    "return Object.keys(localStorage).join(',')"
            );
            if (localStorageKeys != null) {
                for (String key : localStorageKeys.split(",")) {
                    if (key.toLowerCase().contains("token") ||
                            key.toLowerCase().contains("auth") ||
                            key.toLowerCase().contains("session")) {
                        String value = (String) js.executeScript(
                                "return localStorage.getItem(arguments[0])", key
                        );
                        if (value != null && !value.isEmpty()) {
                            log.info("从 localStorage 提取 Token (key={}): {}***",
                                    key, value.substring(0, Math.min(10, value.length())));
                            return value;
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("从 localStorage 提取 Token 失败: {}", e.getMessage());
        }

        // 策略 3: sessionStorage 中提取
        try {
            String sessionStorageKeys = (String) js.executeScript(
                    "return Object.keys(sessionStorage).join(',')"
            );
            if (sessionStorageKeys != null) {
                for (String key : sessionStorageKeys.split(",")) {
                    if (key.toLowerCase().contains("token") ||
                            key.toLowerCase().contains("auth")) {
                        String value = (String) js.executeScript(
                                "return sessionStorage.getItem(arguments[0])", key
                        );
                        if (value != null && !value.isEmpty()) {
                            log.info("从 sessionStorage 提取 Token (key={}): {}***",
                                    key, value.substring(0, Math.min(10, value.length())));
                            return value;
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("从 sessionStorage 提取 Token 失败: {}", e.getMessage());
        }

        // 策略 4: 启发式匹配任意长字符串
        try {
            String allStorage = (String) js.executeScript(
                    "let values = [];" +
                            "Object.values(localStorage).forEach(v => { if (v.length > 50) values.push(v); });" +
                            "Object.values(sessionStorage).forEach(v => { if (v.length > 50) values.push(v); });" +
                            "return values[0] || ''"
            );
            if (allStorage != null && !allStorage.isEmpty()) {
                log.info("启发式匹配 Token: {}***",
                        allStorage.substring(0, Math.min(10, allStorage.length())));
                return allStorage;
            }
        } catch (Exception e) {
            log.warn("启发式匹配 Token 失败: {}", e.getMessage());
        }

        log.error("所有策略均未找到 Token");
        return null;
    }

    /**
     * 同步 Token 到 ZXRequestUtils 供后续交易接口使用
     *
     * @param token Token 字符串
     */
    public void syncTokenToZXRequestUtils(String token) {
        if (token == null || token.isEmpty()) {
            throw new TokenException("Token 为空，无法同步");
        }

        zxRequestUtils.setToken(token);
        log.info("Token 已同步到 ZXRequestUtils: {}***",
                token.substring(0, Math.min(10, token.length())));
    }
}
