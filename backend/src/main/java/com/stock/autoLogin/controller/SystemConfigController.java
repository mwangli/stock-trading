package com.stock.autoLogin.controller;

import com.stock.autoLogin.dto.SystemConfigDto;
import com.stock.autoLogin.service.SystemConfigService;
import com.stock.autoLogin.util.AesEncryptor;
import com.stock.dataCollector.domain.dto.ResponseDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 系统配置控制器
 * <p>
 * 提供系统配置的读取和保存接口，配置数据存储在 Redis 中。
 * </p>
 *
 * @author mwangli
 * @since 2026-03-31
 */
@Slf4j
@RestController
@RequestMapping("/api/system")
@RequiredArgsConstructor
public class SystemConfigController {

    private static final String REDIS_KEY_ENCRYPTION_KEY = "system:config:encryptionKey";

    private final SystemConfigService systemConfigService;

    @Autowired(required = false)
    private RedisTemplate<String, Object> redisTemplate;

    private final AesEncryptor aesEncryptor = new AesEncryptor();

    /**
     * 获取加密密钥
     * <p>
     * 如果密钥已存在则返回已有密钥，否则生成新密钥并存储。
     * </p>
     *
     * @return ResponseDTO<Map<String, String>>
     */
    @GetMapping("/encryptionKey")
    public ResponseDTO<Map<String, String>> getEncryptionKey() {
        log.info("[SystemConfig] 获取加密密钥");

        String encryptionKey;
        if (redisTemplate != null) {
            try {
                Object existingKey = redisTemplate.opsForValue().get(REDIS_KEY_ENCRYPTION_KEY);
                log.info("[SystemConfig] Redis 查询结果: {}", existingKey);
                if (existingKey != null) {
                    encryptionKey = existingKey.toString();
                    log.info("[SystemConfig] 使用已有加密密钥");
                } else {
                    encryptionKey = aesEncryptor.generateSecretKey();
                    log.info("[SystemConfig] 生成新加密密钥: {}", encryptionKey);
                    redisTemplate.opsForValue().set(REDIS_KEY_ENCRYPTION_KEY, encryptionKey);
                    log.info("[SystemConfig] 密钥已写入 Redis");

                    Object verifyKey = redisTemplate.opsForValue().get(REDIS_KEY_ENCRYPTION_KEY);
                    log.info("[SystemConfig] Redis 验证查询: {}", verifyKey);
                }
            } catch (Exception e) {
                log.error("[SystemConfig] Redis 操作失败", e);
                encryptionKey = aesEncryptor.generateSecretKey();
                log.warn("[SystemConfig] Redis 操作失败，使用临时密钥");
            }
        } else {
            encryptionKey = aesEncryptor.generateSecretKey();
            log.warn("[SystemConfig] redisTemplate 为 null，使用临时密钥");
        }

        Map<String, String> result = new HashMap<>();
        result.put("encryptionKey", encryptionKey);
        return ResponseDTO.success(result);
    }

    /**
     * 获取系统配置
     *
     * @return ResponseDTO<SystemConfigDto>
     */
    @GetMapping("/config")
    public ResponseDTO<SystemConfigDto> getConfig() {
        log.info("[SystemConfig] 获取系统配置");
        SystemConfigDto config = systemConfigService.getConfig();
        return ResponseDTO.success(config);
    }

    /**
     * 保存系统配置
     *
     * @param config 系统配置对象
     * @return ResponseDTO<String>
     */
    @PutMapping("/config")
    public ResponseDTO<String> saveConfig(@RequestBody SystemConfigDto config) {
        log.info("[SystemConfig] 保存系统配置");
        systemConfigService.saveConfig(config);
        return ResponseDTO.success(null, "配置已保存");
    }
}
