package com.stock.autoLogin.controller;

import com.stock.autoLogin.dto.SystemConfigDto;
import com.stock.autoLogin.service.SystemConfigService;
import com.stock.dataCollector.domain.dto.ResponseDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

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

    private final SystemConfigService systemConfigService;

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
