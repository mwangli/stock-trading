// AI_GENERATE_START-----
package com.stock.agent.controller;

import com.stock.agent.dto.AgentApiBudgetResponse;
import com.stock.agent.dto.AgentHealthResponse;
import com.stock.agent.dto.ProviderModeRequest;
import com.stock.agent.dto.ProviderModeResponse;
import com.stock.agent.dto.RuntimeStatus;
import com.stock.agent.runtime.AgentApiBudgetManager;
import com.stock.agent.service.AgentRuntimeManagementService;
import com.stock.agent.api.ResponseDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Agent 运行状态、Provider 和费用预算控制器。
 *
 * @author mwangli
 * @since 2026-10-23
 */
@Slf4j
@RestController
@RequestMapping("/api/agent/runtime")
@RequiredArgsConstructor
public class AgentRuntimeController {

    private final AgentRuntimeManagementService runtimeManagementService;
    private final AgentApiBudgetManager apiBudgetService;

    /**
     * 查询 Agent 运行状态。
     *
     * @return 运行状态
     */
    @GetMapping("/status")
    public ResponseDTO<RuntimeStatus> runtimeStatus() {
        log.info("[Agent] 查询运行状态");
        return ResponseDTO.success(runtimeManagementService.runtimeStatus());
    }

    /**
     * 检查模型 Provider 健康状态。
     *
     * @return Provider 健康状态
     */
    @GetMapping("/health")
    public ResponseDTO<AgentHealthResponse> runtimeHealth() {
        log.info("[Agent] 检查模型 Provider 健康状态");
        return ResponseDTO.success(runtimeManagementService.healthCheck());
    }

    /**
     * 动态切换 Agent Provider。
     *
     * @param request Provider 切换请求
     * @return 当前 Provider 模式
     */
    @PutMapping("/provider")
    public ResponseDTO<ProviderModeResponse> switchProvider(@RequestBody ProviderModeRequest request) {
        log.info("[Agent] 切换模型 Provider: provider={}", request == null ? null : request.getProvider());
        return ResponseDTO.success(runtimeManagementService.switchProvider(request));
    }

    /**
     * 查询北京时间当天的 Agent API 费用预算。
     *
     * @return 当日费用预算状态
     */
    @GetMapping("/apiBudget")
    public ResponseDTO<AgentApiBudgetResponse> apiBudget() {
        log.info("[Agent] 查询当日API费用预算");
        return ResponseDTO.success(AgentApiBudgetResponse.from(apiBudgetService.currentSnapshot()));
    }
}
// AI_GENERATE_END-----
