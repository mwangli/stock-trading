// AI_GENERATE_START --
package com.stock.tradingExecutor.execution;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 中信证券 Token Redis 存储组件。
 * Token 只保存在 Redis，并按照券商配置设置过期时间。
 *
 * @author mwangli
 * @since 2026-09-30
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ZXBrokerTokenStore {

    private final StringRedisTemplate redisTemplate;
    private final ZXBrokerConfig brokerConfig;

    /**
     * 从 Redis 获取券商 Token。
     *
     * @return 有效 Token，键不存在时返回 null
     * @throws IllegalStateException Redis 访问失败时抛出
     */
    public String getToken() {
        try {
            String token = redisTemplate.opsForValue().get(brokerConfig.getTokenRedisKey());
            return token == null || token.isBlank() ? null : token;
        } catch (RuntimeException exception) {
            log.error("[ZXBrokerTokenStore] Redis Token 读取失败", exception);
            throw new IllegalStateException("Redis 不可用，无法获取券商 Token", exception);
        }
    }

    /**
     * 将券商 Token 写入 Redis，并设置配置指定的分钟级过期时间。
     *
     * @param token 券商 Token
     * @throws IllegalArgumentException Token 为空时抛出
     * @throws IllegalStateException Redis 写入失败时抛出
     */
    public void saveToken(String token) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("券商 Token 不能为空");
        }
        try {
            Duration ttl = Duration.ofMinutes(brokerConfig.getTokenExpireMinutes());
            redisTemplate.opsForValue().set(brokerConfig.getTokenRedisKey(), token, ttl);
            log.info("[ZXBrokerTokenStore] 券商 Token 已写入 Redis，过期分钟数={}", ttl.toMinutes());
        } catch (RuntimeException exception) {
            log.error("[ZXBrokerTokenStore] Redis Token 写入失败", exception);
            throw new IllegalStateException("Redis 不可用，无法保存券商 Token", exception);
        }
    }

}
// AI_GENERATE_END --
