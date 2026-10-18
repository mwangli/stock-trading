// AI_GENERATE_START- ---
package com.stock.tradingExecutor.api;

import com.stock.dataCollector.domain.dto.ResponseDTO;
import com.stock.tradingExecutor.domain.dto.SystemRuntimeConfigDto;
import com.stock.tradingExecutor.service.SystemRuntimeConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 系统运行控制配置接口。
 * 提供非敏感运行开关的查询和保存能力。
 *
 * @author mwangli
 * @since 2026-10-12
 */
@Slf4j
@RestController
@RequestMapping("/api/systemSettings/runtime")
@RequiredArgsConstructor
public class SystemRuntimeConfigController {

    private final SystemRuntimeConfigService configService;

    /**
     * 查询当前运行控制配置。
     *
     * @return 当前生效配置
     */
    @GetMapping
    public ResponseDTO<SystemRuntimeConfigDto.SettingsResponse> getSettings() {
        log.info("[系统配置] 查询运行控制配置");
        try {
            return ResponseDTO.success(configService.getSettings());
        } catch (RuntimeException exception) {
            log.error("[系统配置] 查询运行控制配置失败", exception);
            return ResponseDTO.error(exception.getMessage());
        }
    }

    /**
     * 保存运行控制配置。
     *
     * @param request 保存请求
     * @return 保存后的生效配置
     */
    @PutMapping
    public ResponseDTO<SystemRuntimeConfigDto.SettingsResponse> updateSettings(
            @RequestBody SystemRuntimeConfigDto.UpdateRequest request) {
        log.info("[系统配置] 保存运行控制配置");
        try {
            return ResponseDTO.success(configService.updateSettings(request));
        } catch (RuntimeException exception) {
            log.error("[系统配置] 保存运行控制配置失败", exception);
            return ResponseDTO.error(exception.getMessage());
        }
    }
}
// AI_GENERATE_END ---
