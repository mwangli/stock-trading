// AI_GENERATE_START ------
package com.stock.strategyAgent.api;

import com.stock.dataCollector.domain.dto.ResponseDTO;
import com.stock.strategyAgent.rag.AgentRagService;
import com.stock.strategyAgent.service.AgentApplicationService;
import com.stock.strategyAgent.service.AgentMemoryService;
import com.stock.strategyAgent.service.TradePlanService;
import com.stock.strategyAgent.service.TradePlanAgentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * AGENT 模块 REST 接口。
 *
 * @author mwangli
 * @since 2026-10-17
 */
@Slf4j
@RestController
@RequestMapping("/api/agent")
@RequiredArgsConstructor
public class AgentController {

    private final AgentApplicationService applicationService;
    private final AgentMemoryService memoryService;
    private final AgentRagService ragService;
    private final TradePlanService tradePlanService;
    private final TradePlanAgentService tradePlanAgentService;

    /**
     * 创建 Agent 会话。
     *
     * @param request 创建请求
     * @return 会话响应
     */
    @PostMapping("/sessions")
    public ResponseDTO<SessionResponse> createSession(@RequestBody CreateSessionRequest request) {
        log.info("[Agent] 创建会话: userScope={}", request == null ? null : request.getUserScope());
        return ResponseDTO.success(applicationService.createSession(request));
    }

    /**
     * 查询 Agent 会话。
     *
     * @param sessionId 会话标识
     * @return 会话响应
     */
    @GetMapping("/sessions/{sessionId}")
    public ResponseDTO<SessionResponse> getSession(@PathVariable String sessionId) {
        log.info("[Agent] 查询会话: sessionId={}", sessionId);
        return ResponseDTO.success(applicationService.getSession(sessionId));
    }

    /**
     * 提交 Research 任务。
     *
     * @param request Research 请求
     * @return 任务响应
     */
    @PostMapping("/tasks/research")
    public ResponseDTO<TaskResponse> research(@RequestBody ResearchRequest request) {
        log.info("[Agent] 执行 Research 任务");
        return ResponseDTO.success(applicationService.research(request));
    }

    /**
     * 提交 Diagnostic 任务。
     *
     * @param request 诊断请求
     * @return 任务响应
     */
    @PostMapping("/tasks/diagnose")
    public ResponseDTO<TaskResponse> diagnose(@RequestBody DiagnosticRequest request) {
        log.info("[Agent] 执行运行诊断: jobName={}", request == null ? null : request.getJobName());
        return ResponseDTO.success(applicationService.diagnose(request));
    }

    /**
     * 生成结构化交易计划草案。
     *
     * @param request 生成请求
     * @return 待重新复核的交易计划
     */
    @PostMapping("/tasks/tradePlan")
    public ResponseDTO<TradePlanDto.Response> tradePlan(@RequestBody TradePlanDraftRequest request) {
        log.info("[Agent] 生成交易计划草案: candidateBatchId={}",
                request == null ? null : request.getCandidateBatchId());
        return ResponseDTO.success(tradePlanAgentService.generate(request));
    }

    /**
     * 生成每日投研报告。
     *
     * @param request 报告请求
     * @return 任务响应
     */
    @PostMapping("/reports/daily")
    public ResponseDTO<TaskResponse> dailyReport(@RequestBody ReportRequest request) {
        log.info("[Agent] 生成每日投研报告");
        return ResponseDTO.success(applicationService.dailyReport(request));
    }

    /**
     * 查询 Agent 任务。
     *
     * @param taskId 任务标识
     * @return 任务响应
     */
    @GetMapping("/tasks/{taskId}")
    public ResponseDTO<TaskResponse> getTask(@PathVariable String taskId) {
        log.info("[Agent] 查询任务: taskId={}", taskId);
        return ResponseDTO.success(applicationService.getTask(taskId));
    }

    /**
     * 查询任务证据摘要。
     *
     * @param taskId 任务标识
     * @return 任务响应
     */
    @GetMapping("/tasks/{taskId}/evidence")
    public ResponseDTO<TaskResponse> getEvidence(@PathVariable String taskId) {
        log.info("[Agent] 查询任务证据: taskId={}", taskId);
        return ResponseDTO.success(applicationService.getTask(taskId));
    }

    /**
     * 取消 Agent 任务。
     *
     * @param taskId 任务标识
     * @return 空响应
     */
    @PostMapping("/tasks/{taskId}/cancel")
    public ResponseDTO<Void> cancel(@PathVariable String taskId) {
        log.info("[Agent] 取消任务: taskId={}", taskId);
        applicationService.cancel(taskId);
        return ResponseDTO.success(null);
    }

    /**
     * 查询 Agent 运行状态。
     *
     * @return 运行状态
     */
    @GetMapping("/runtime/status")
    public ResponseDTO<RuntimeStatus> runtimeStatus() {
        log.info("[Agent] 查询运行状态");
        return ResponseDTO.success(applicationService.runtimeStatus());
    }

    /**
     * 检查 Agent Provider 是否可以完成实际模型调用。
     *
     * @return Provider 健康状态
     */
    @GetMapping("/runtime/health")
    public ResponseDTO<AgentHealthResponse> runtimeHealth() {
        log.info("[Agent] 检查模型 Provider 健康状态");
        return ResponseDTO.success(applicationService.healthCheck());
    }

    /**
     * 动态切换 Agent Provider。
     *
     * @param request Provider 切换请求
     * @return 当前 Provider 模式
     */
    @PutMapping("/runtime/provider")
    public ResponseDTO<ProviderModeResponse> switchProvider(@RequestBody ProviderModeRequest request) {
        log.info("[Agent] 切换模型 Provider: provider={}", request == null ? null : request.getProvider());
        return ResponseDTO.success(applicationService.switchProvider(request));
    }

    /**
     * 创建 Memory。
     *
     * @param request 创建请求
     * @return Memory 响应
     */
    @PostMapping("/memories")
    public ResponseDTO<MemoryResponse> createMemory(@RequestBody CreateMemoryRequest request) {
        log.info("[Agent] 创建 Memory: userScope={}", request == null ? null : request.getUserScope());
        return ResponseDTO.success(memoryService.create(request));
    }

    /**
     * 查询 Memory。
     *
     * @param userScope 用户范围
     * @return Memory 列表
     */
    @GetMapping("/memories")
    public ResponseDTO<List<MemoryResponse>> listMemories(String userScope) {
        log.info("[Agent] 查询 Memory: userScope={}", userScope);
        return ResponseDTO.success(memoryService.list(userScope));
    }

    /**
     * 删除 Memory。
     *
     * @param memoryId Memory 标识
     * @return 空响应
     */
    @DeleteMapping("/memories/{memoryId}")
    public ResponseDTO<Void> forgetMemory(@PathVariable String memoryId) {
        log.info("[Agent] 删除 Memory: memoryId={}", memoryId);
        memoryService.forget(memoryId);
        return ResponseDTO.success(null);
    }

    /**
     * 修正 Memory。
     *
     * @param memoryId Memory 标识
     * @param request 修正请求
     * @return 修正结果
     */
    @PostMapping("/memories/{memoryId}/correct")
    public ResponseDTO<MemoryResponse> correctMemory(@PathVariable String memoryId,
                                                                @RequestBody CorrectMemoryRequest request) {
        log.info("[Agent] 修正 Memory: memoryId={}", memoryId);
        return ResponseDTO.success(memoryService.correct(memoryId, request));
    }

    /**
     * 写入 RAG 文档。
     *
     * @param request 文档请求
     * @return 写入 Chunk 数量
     */
    @PostMapping("/rag/documents")
    public ResponseDTO<Integer> ingestRag(@RequestBody RagIngestRequest request) {
        log.info("[Agent] 写入 RAG 文档: sourceId={}", request == null ? null : request.getSourceId());
        return ResponseDTO.success(ragService.ingest(request));
    }

    /**
     * 检索 RAG 文档。
     *
     * @param request 检索请求
     * @return RAG 结果
     */
    @PostMapping("/rag/search")
    public ResponseDTO<List<RagResult>> searchRag(@RequestBody RagSearchRequest request) {
        log.info("[Agent] 检索 RAG 文档");
        return ResponseDTO.success(ragService.search(request));
    }

    /**
     * 创建自动交易计划，不包含人工审批步骤。
     *
     * @param request 创建请求
     * @return 交易计划
     */
    @PostMapping("/tradePlans")
    public ResponseDTO<TradePlanDto.Response> createTradePlan(@RequestBody TradePlanDto.CreateRequest request) {
        log.info("[Agent] 创建自动交易计划: candidateBatchId={}",
                request == null ? null : request.getCandidateBatchId());
        return ResponseDTO.success(tradePlanService.create(request));
    }

    /**
     * 查询自动交易计划。
     *
     * @param planId 计划标识
     * @return 交易计划
     */
    @GetMapping("/tradePlans/{planId}")
    public ResponseDTO<TradePlanDto.Response> getTradePlan(@PathVariable String planId) {
        log.info("[Agent] 查询自动交易计划: planId={}", planId);
        return ResponseDTO.success(tradePlanService.get(planId));
    }

    /**
     * 触发计划级自动复核，不代表人工审批。
     *
     * @param planId 计划标识
     * @return 复核后的计划
     */
    @PostMapping("/tradePlans/{planId}/revalidate")
    public ResponseDTO<TradePlanDto.Response> revalidateTradePlan(@PathVariable String planId) {
        log.info("[Agent] 自动复核交易计划: planId={}", planId);
        return ResponseDTO.success(tradePlanService.revalidate(planId));
    }
}
// AI_GENERATE_END -----
