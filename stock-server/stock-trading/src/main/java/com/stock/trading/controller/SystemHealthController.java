// AI_GENERATE_START-------
package com.stock.trading.controller;

import com.stock.base.web.ResponseDTO;
import com.stock.trading.dto.SystemHealthDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 提供不依赖登录态和业务依赖的后端健康检查接口。
 *
 * @author mwangli
 * @since 2026-10-17
 */
@Slf4j
@RestController
@RequestMapping("/api/system")
public class SystemHealthController {

    /**
     * 检查后端进程是否可以正常接收 HTTP 请求。
     *
     * @return 后端进程健康状态
     */
    @GetMapping("/health")
    public ResponseDTO<SystemHealthDto> health() {
        log.info("检查后端系统健康状态");
        return ResponseDTO.success(SystemHealthDto.builder()
                .status("UP")
                .service("AI-AGENT智能股票自动交易系统后端")
                .build());
    }
}
// AI_GENERATE_END-------
