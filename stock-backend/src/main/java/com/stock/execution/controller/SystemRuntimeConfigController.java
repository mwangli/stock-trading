// AI_GENERATE_START----------
package com.stock.execution.controller;

import com.stock.platform.dto.ResponseDTO;
import com.stock.execution.dto.SystemRuntimeConfigResponse;
import com.stock.execution.dto.SystemRuntimeConfigUpdateRequest;
import com.stock.execution.service.SystemRuntimeConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 系统运行控制配置接口。
 * 提供调度器和应用启动任务设置的查询与保存能力。
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
    public ResponseDTO<SystemRuntimeConfigResponse> getSettings() {
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
    public ResponseDTO<SystemRuntimeConfigResponse> updateSettings(
            @RequestBody SystemRuntimeConfigUpdateRequest request) {
        log.info("[系统配置] 保存运行控制配置");
        try {
            return ResponseDTO.success(configService.updateSettings(request));
        } catch (RuntimeException exception) {
            log.error("[系统配置] 保存运行控制配置失败", exception);
            return ResponseDTO.error(exception.getMessage());
        }
    }
}
// AI_GENERATE_END----------
