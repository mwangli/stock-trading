package com.stock.tradingExecutor.execution;

import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.Cookie;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Set;

/**
 * 从与自动登录共用的 {@link BrowserSessionManager} 会话中读取 Cookie / Token，
 * 必要时回退到 localStorage / sessionStorage。
 *
 * @author mwangli
 * @since 2026-03-22
 */
@Slf4j
@Component
public class CookieManager {

    private final BrowserSessionManager browserSessionManager;

    public CookieManager(BrowserSessionManager browserSessionManager) {
        this.browserSessionManager = browserSessionManager;
    }

    /**
     * 获取当前浏览器中的全部 Cookie。
     *
     * @return Cookie 集合；浏览器未启动时为空集
     */
    public Set<Cookie> getCookies() {
        WebDriver driver = currentDriver();
        if (driver == null) {
            log.warn("[CookieManager] 浏览器未启动，无法读取 Cookie");
            return Set.of();
        }
        return driver.manage().getCookies();
    }

    /**
     * 按名称读取 Cookie 值。
     *
     * @param cookieName Cookie 名称
     * @return 值；不存在或未启动浏览器时为 null
     */
    public String getCookieValue(String cookieName) {
        WebDriver driver = currentDriver();
        if (driver == null) {
            return null;
        }
        Cookie cookie = driver.manage().getCookieNamed(cookieName);
        return cookie != null ? cookie.getValue() : null;
    }

    /**
     * 解析登录 Token：常见 Cookie 名 → 全部 Cookie 启发式 → localStorage / sessionStorage（含 key 扫描）。
     *
     * @return Token 字符串；未找到时为 null
     */
    public String getToken() {
        WebDriver driver = currentDriver();
        if (driver == null) {
            log.debug("[CookieManager] 浏览器未运行，跳过 Token 读取");
            return null;
        }
        browserSessionManager.switchToDefaultContent();

        String[] names = {"token", "Token", "TOKEN", "session_token", "auth_token", "access_token", "jwt",
                "tk", "SESSION", "sessionId", "sessionid", "JSESSIONID", "Authorization"};
        for (String name : names) {
            String v = getCookieValue(name);
            if (v != null && !v.isBlank()) {
                log.info("[CookieManager] 从 Cookie 获取 Token: 名称匹配 {}", name);
                return v.trim();
            }
        }

        for (Cookie c : driver.manage().getCookies()) {
            String val = c.getValue();
            if (val == null || val.isBlank()) {
                continue;
            }
            String ln = c.getName().toLowerCase(Locale.ROOT);
            if ((ln.contains("token") || ln.contains("session") || ln.contains("sid")
                    || ln.contains("auth") || ln.equals("tk") || ln.contains("jwt"))
                    && looksLikeSessionSecret(val)) {
                log.info("[CookieManager] 从 Cookie 启发式匹配名称: {}", c.getName());
                return val.trim();
            }
        }

        if (driver instanceof JavascriptExecutor js) {
            try {
                Object o = js.executeScript("""
                        function scan(store) {
                          if (!store || !store.length) return null;
                          const prefer = ['token','Token','TOKEN','access_token','accessToken','tk',
                            'sessionId','session_id','Authorization','authToken','userToken'];
                          for (const k of prefer) {
                            const v = store.getItem(k);
                            if (v && String(v).length >= 8) return String(v);
                          }
                          for (let i = 0; i < store.length; i++) {
                            const k = store.key(i);
                            if (!k) continue;
                            const kl = k.toLowerCase();
                            if (kl.includes('token') || kl.includes('session') || kl.includes('auth')) {
                              const v = store.getItem(k);
                              if (v && String(v).length >= 12) return String(v);
                            }
                          }
                          return null;
                        }
                        let r = scan(window.localStorage) || scan(window.sessionStorage);
                        if (!r) {
                          try {
                            r = scan(window.top.localStorage) || scan(window.top.sessionStorage);
                          } catch (e) {}
                        }
                        return r;
                        """);
                if (o instanceof String s && !s.isBlank()) {
                    log.info("[CookieManager] 从 Web Storage 获取 Token");
                    return s.trim();
                }
            } catch (Exception e) {
                log.debug("[CookieManager] 执行脚本读取 Token 失败: {}", e.getMessage());
            }
        }
        log.debug("[CookieManager] 本轮未解析到 Token");
        return null;
    }

    private static boolean looksLikeSessionSecret(String value) {
        if (value.length() < 12) {
            return false;
        }
        String t = value.trim();
        if (t.equalsIgnoreCase("true") || t.equalsIgnoreCase("false")) {
            return false;
        }
        return true;
    }

    /**
     * 是否存在非空 Token。
     *
     * @return 是否已解析到 Token
     */
    public boolean hasValidToken() {
        String token = getToken();
        return token != null && !token.isEmpty();
    }

    /**
     * 打印当前所有 Cookie（调试用途，勿在生产长期开启）。
     */
    public void printAllCookies() {
        Set<Cookie> cookies = getCookies();
        log.info("[CookieManager] 当前Cookie数量: {}", cookies.size());
        for (Cookie cookie : cookies) {
            log.info("[CookieManager] Cookie: {}={}, Domain={}, Path={}",
                    cookie.getName(), cookie.getValue(), cookie.getDomain(), cookie.getPath());
        }
    }

    /**
     * 将 Cookie 导出为简单 JSON 数组字符串。
     *
     * @return JSON 文本
     */
    public String exportCookiesAsJson() {
        StringBuilder sb = new StringBuilder("[");
        Set<Cookie> cookies = getCookies();
        int count = 0;
        for (Cookie cookie : cookies) {
            if (count > 0) {
                sb.append(",");
            }
            sb.append("{");
            sb.append("\"name\":\"").append(cookie.getName()).append("\",");
            sb.append("\"value\":\"").append(cookie.getValue()).append("\",");
            sb.append("\"domain\":\"").append(cookie.getDomain()).append("\",");
            sb.append("\"path\":\"").append(cookie.getPath()).append("\",");
            sb.append("\"expiry\":").append(cookie.getExpiry() != null ? cookie.getExpiry().getTime() : "null");
            sb.append("}");
            count++;
        }
        sb.append("]");
        return sb.toString();
    }

    private WebDriver currentDriver() {
        if (!browserSessionManager.isRunning()) {
            return null;
        }
        return browserSessionManager.getDriver();
    }
}
