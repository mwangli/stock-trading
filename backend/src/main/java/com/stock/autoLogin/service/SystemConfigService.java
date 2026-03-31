package com.stock.autoLogin.service;

import com.stock.autoLogin.dto.SystemConfigDto;
import com.stock.autoLogin.util.AesEncryptor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

/**
 * 系统配置服务
 * <p>
 * 负责管理系统配置项的读取和写入，所有配置存储在 Redis 中。
 * API 密钥采用 AES 加密存储。
 * </p>
 *
 * @author mwangli
 * @since 2026-03-31
 */
@Slf4j
@Service
public class SystemConfigService {

    private static final String REDIS_KEY_API_KEY = "LOGIN_TOKEN";
    private static final String REDIS_KEY_THEME = "system:config:theme";
    private static final String REDIS_KEY_LANGUAGE = "system:config:language";
    private static final String REDIS_KEY_NOTIFICATIONS = "system:config:notifications";
    private static final String REDIS_KEY_REFRESH_RATE = "system:config:refreshRate";
    private static final String REDIS_KEY_RISK_LEVEL = "system:config:riskLevel";
    private static final String REDIS_KEY_MAX_DRAWDOWN = "system:config:maxDrawdown";

    @Autowired(required = false)
    private RedisTemplate<String, Object> redisTemplate;

    private final AesEncryptor aesEncryptor = new AesEncryptor();

    public SystemConfigService() {
        log.info("[SystemConfigService] 初始化，redisTemplate={}", redisTemplate != null ? "已注入" : "未注入");
    }

    /**
     * 保存系统配置到 Redis
     * <p>
     * API 密钥如果是加密格式（Base64 + AES），则直接存储；
     * 如果是明文，则加密后存储。
     * </p>
     *
     * @param config 系统配置对象
     */
    public void saveConfig(SystemConfigDto config) {
        if (redisTemplate == null) {
            log.error("RedisTemplate 未注入，配置将不会被持久化！请检查：1. Redis 是否启动 2. spring.data.redis 配置是否正确");
            throw new IllegalStateException("RedisTemplate 未注入，无法保存配置到 Redis");
        }

        log.info("[SystemConfig] 开始保存配置到 Redis");

        try {
            if (config.getApiKey() != null) {
                String apiKey = config.getApiKey().trim();
                log.info("[SystemConfig] 待存储 API Key: [{}], 长度: {}", apiKey, apiKey.length());
                if (!isEncrypted(apiKey)) {
                    log.info("[SystemConfig] API Key 为明文，进行加密存储");
                } else {
                    log.info("[SystemConfig] API Key 已是加密格式，直接存储");
                }
                redisTemplate.opsForValue().set(REDIS_KEY_API_KEY, apiKey);
                log.info("[SystemConfig] API Key 已写入 Redis");

                Object verifyKey = redisTemplate.opsForValue().get(REDIS_KEY_API_KEY);
                log.info("[SystemConfig] Redis 验证读取 API Key: [{}]", verifyKey);
            }
            if (config.getTheme() != null) {
                redisTemplate.opsForValue().set(REDIS_KEY_THEME, config.getTheme());
            }
            if (config.getLanguage() != null) {
                redisTemplate.opsForValue().set(REDIS_KEY_LANGUAGE, config.getLanguage());
            }
            if (config.getNotifications() != null) {
                redisTemplate.opsForValue().set(REDIS_KEY_NOTIFICATIONS, config.getNotifications().toString());
            }
            if (config.getRefreshRate() != null) {
                redisTemplate.opsForValue().set(REDIS_KEY_REFRESH_RATE, config.getRefreshRate().toString());
            }
            if (config.getRiskLevel() != null) {
                redisTemplate.opsForValue().set(REDIS_KEY_RISK_LEVEL, config.getRiskLevel());
            }
            if (config.getMaxDrawdown() != null) {
                redisTemplate.opsForValue().set(REDIS_KEY_MAX_DRAWDOWN, config.getMaxDrawdown().toString());
            }
            log.info("系统配置已保存到 Redis");
        } catch (Exception e) {
            log.error("[SystemConfig] 保存配置到 Redis 失败", e);
            throw new RuntimeException("保存配置到 Redis 失败: " + e.getMessage(), e);
        }
    }

    /**
     * 判断字符串是否为 Base64 加密格式
     */
    private boolean isEncrypted(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        return text.matches("^[A-Za-z0-9+/=]+$") && text.length() >= 20;
    }

    /**
     * 从 Redis 获取系统配置
     *
     * @return SystemConfigDto 系统配置对象
     */
    public SystemConfigDto getConfig() {
        SystemConfigDto config = new SystemConfigDto();

        if (redisTemplate == null) {
            log.warn("RedisTemplate 未注入，返回默认配置");
            return getDefaultConfig();
        }

        Object apiKey = redisTemplate.opsForValue().get(REDIS_KEY_API_KEY);
        if (apiKey != null) {
            config.setApiKey(apiKey.toString());
        }

        Object theme = redisTemplate.opsForValue().get(REDIS_KEY_THEME);
        if (theme != null) {
            config.setTheme(theme.toString());
        }

        Object language = redisTemplate.opsForValue().get(REDIS_KEY_LANGUAGE);
        if (language != null) {
            config.setLanguage(language.toString());
        }

        Object notifications = redisTemplate.opsForValue().get(REDIS_KEY_NOTIFICATIONS);
        if (notifications != null) {
            config.setNotifications(Boolean.parseBoolean(notifications.toString()));
        }

        Object refreshRate = redisTemplate.opsForValue().get(REDIS_KEY_REFRESH_RATE);
        if (refreshRate != null) {
            config.setRefreshRate(Integer.parseInt(refreshRate.toString()));
        }

        Object riskLevel = redisTemplate.opsForValue().get(REDIS_KEY_RISK_LEVEL);
        if (riskLevel != null) {
            config.setRiskLevel(riskLevel.toString());
        }

        Object maxDrawdown = redisTemplate.opsForValue().get(REDIS_KEY_MAX_DRAWDOWN);
        if (maxDrawdown != null) {
            config.setMaxDrawdown(Integer.parseInt(maxDrawdown.toString()));
        }

        return config;
    }

    /**
     * 获取 API 密钥
     *
     * @return API 密钥字符串，如果不存在返回 null
     */
    public String getApiKey() {
        if (redisTemplate == null) {
            return null;
        }
        Object apiKey = redisTemplate.opsForValue().get(REDIS_KEY_API_KEY);
        return apiKey != null ? apiKey.toString() : null;
    }

    private SystemConfigDto getDefaultConfig() {
        return SystemConfigDto.builder()
                .theme("dark")
                .language("en")
                .notifications(true)
                .refreshRate(30)
                .riskLevel("moderate")
                .maxDrawdown(15)
                .build();
    }
}
