// AI_GENERATE_START ---
package com.stock.tradingExecutor.execution;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 中信证券 Token 进程内缓存组件。
 * Token 仅在当前 Backend 进程内保存，并按照券商配置设置过期时间；
 * 应用重启后缓存自动清空，由登录流程重新获取 Token。
 *
 * @author mwangli
 * @since 2026-09-30
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ZXBrokerTokenStore {

    private final ZXBrokerConfig brokerConfig;
    private volatile TokenEntry currentToken;

    /**
     * 获取当前进程内仍然有效的券商 Token。
     *
     * @return 有效 Token，不存在或已过期时返回 null
     */
    public String getToken() {
        TokenEntry tokenEntry = currentToken;
        if (tokenEntry == null) {
            return null;
        }
        if (System.currentTimeMillis() >= tokenEntry.expireAtMillis()) {
            synchronized (this) {
                if (currentToken == tokenEntry) {
                    currentToken = null;
                }
            }
            return null;
        }
        return tokenEntry.token();
    }

    /**
     * 将券商 Token 写入当前进程内缓存，并设置分钟级过期时间。
     *
     * @param token 券商 Token
     * @throws IllegalArgumentException Token 为空时抛出
     */
    public synchronized void saveToken(String token) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("券商 Token 不能为空");
        }
        Integer configuredMinutes = brokerConfig.getTokenExpireMinutes();
        long ttlMinutes = configuredMinutes == null ? 30L : Math.max(1L, configuredMinutes.longValue());
        Duration ttl = Duration.ofMinutes(ttlMinutes);
        currentToken = new TokenEntry(token, System.currentTimeMillis() + ttl.toMillis());
        log.info("[ZXBrokerTokenStore] 券商 Token 已写入进程内缓存，过期分钟数={}", ttlMinutes);
    }

    private record TokenEntry(String token, long expireAtMillis) {
    }
}
// AI_GENERATE_END ---
