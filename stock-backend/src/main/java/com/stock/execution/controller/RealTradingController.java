// AI_GENERATE_START---------
package com.stock.execution.controller;

import com.stock.decision.config.StrategyConfigManager;
import com.stock.platform.dto.ResponseDTO;
import com.stock.execution.dto.RealTradingStatusDto;
import com.stock.execution.dto.AutomaticTradingEmergencyStopRequest;
import com.stock.execution.dto.SystemRuntimeConfigResponse;
import com.stock.execution.dto.TradingCandidateListResponseDto;
import com.stock.execution.service.RealTradingService;
import com.stock.execution.service.SystemRuntimeConfigService;
import com.stock.execution.interceptor.AuthInterceptor;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.function.Supplier;

/**
 * 真实交易控制器。
 * 提供自动交易状态、候选查询和运维检查入口。
 *
 * @author mwangli
 * @since 2026-09-25
 */
@Slf4j
@RestController
@RequestMapping("/api/realTrading")
@RequiredArgsConstructor
public class RealTradingController {

    private final RealTradingService realTradingService;
    private final SystemRuntimeConfigService runtimeConfigService;
    private final StrategyConfigManager strategyConfigManager;

    /**
     * 查询真实交易运行状态。
     *
     * @return 真实交易状态
     */
    @GetMapping("/status")
    public ResponseDTO<RealTradingStatusDto> getStatus() {
        log.info("查询真实交易运行状态");
        return execute(realTradingService::getStatus);
    }

    /**
     * 查询当日真实模型交易候选。
     *
     * @return 候选列表
     */
    @GetMapping("/candidates")
    public ResponseDTO<TradingCandidateListResponseDto> getCandidates() {
        log.info("查询当日真实模型交易候选");
        return execute(realTradingService::getCandidates);
    }

    /**
     * 紧急停止后续自动交易。
     *
     * @param request 紧急停止请求
     * @param httpRequest HTTP 请求
     * @return 保存后的运行控制状态
     */
    @PostMapping("/emergencyStop")
    public ResponseDTO<SystemRuntimeConfigResponse> emergencyStop(
            @RequestBody AutomaticTradingEmergencyStopRequest request,
            HttpServletRequest httpRequest) {
        log.warn("收到自动交易紧急停止请求");
        return execute(() -> runtimeConfigService.emergencyStopAutomaticTrading(
                request == null ? null : request.getReason(),
                currentOperator(httpRequest),
                currentStrategyVersion()));
    }

    private String currentOperator(HttpServletRequest request) {
        if (request.getSession(false) != null) {
            Object username = request.getSession(false)
                    .getAttribute(AuthInterceptor.AUTH_USERNAME_SESSION_KEY);
            if (username instanceof String value && !value.isBlank()) {
                return value;
            }
        }
        return "UNKNOWN";
    }

    private String currentStrategyVersion() {
        try {
            return strategyConfigManager.getCurrentConfigDto().getVersion();
        } catch (RuntimeException exception) {
            log.warn("紧急停止时读取策略版本失败: {}", exception.getMessage());
            return "UNAVAILABLE";
        }
    }

    private <T> ResponseDTO<T> execute(Supplier<T> supplier) {
        try {
            return ResponseDTO.success(supplier.get());
        } catch (IllegalArgumentException | IllegalStateException exception) {
            log.warn("真实交易请求未执行: {}", exception.getMessage());
            return ResponseDTO.error(exception.getMessage());
        } catch (RuntimeException exception) {
            log.error("真实交易请求执行异常", exception);
            return ResponseDTO.error("真实交易执行失败，请检查服务日志");
        }
    }
}
// AI_GENERATE_END---------
