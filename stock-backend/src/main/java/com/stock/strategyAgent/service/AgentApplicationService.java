// AI_GENERATE_START ---------------------------------
package com.stock.strategyAgent.service;

import com.stock.strategyAgent.api.DiagnosticRequest;

import com.alibaba.fastjson2.JSON;
import com.stock.strategyAgent.api.CreateSessionRequest;
import com.stock.strategyAgent.api.ReportRequest;
import com.stock.strategyAgent.api.ResearchRequest;
import com.stock.strategyAgent.capability.AgentToolName;
import com.stock.strategyAgent.api.RuntimeStatus;
import com.stock.strategyAgent.api.AgentHealthResponse;
import com.stock.strategyAgent.api.ProviderModeRequest;
import com.stock.strategyAgent.api.ProviderModeResponse;
import com.stock.strategyAgent.api.SessionResponse;
import com.stock.strategyAgent.api.TaskResponse;
import com.stock.strategyAgent.config.AgentProperties;
import com.stock.strategyAgent.domain.AgentEnums;
import com.stock.strategyAgent.domain.entity.AgentSessionEntity;
import com.stock.strategyAgent.domain.entity.AgentTaskEntity;
import com.stock.strategyAgent.evidence.EvidenceAssembler;
import com.stock.strategyAgent.evidence.EvidenceBundle;
import com.stock.strategyAgent.persistence.AgentSessionRepository;
import com.stock.strategyAgent.persistence.AgentTaskRepository;
import com.stock.strategyAgent.rag.EmbeddingRuntimeStatus;
import com.stock.strategyAgent.rag.LangChainEmbeddingService;
import com.stock.strategyAgent.runtime.AgentRuntimeRequest;
import com.stock.strategyAgent.runtime.AgentRuntimeResponse;
import com.stock.strategyAgent.runtime.AgentRuntimeService;
import com.stock.strategyAgent.runtime.AgentRuntimeStatus;
import com.stock.strategyAgent.runtime.AgentStructuredOutput;
import com.stock.strategyAgent.runtime.DiagnosticResult;
import com.stock.strategyAgent.tool.AgentToolContextHolder;
import com.stock.strategyAgent.tool.ToolExecutionContext;
import com.stock.strategyAgent.tool.read.JobDiagnosticTool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Agent 会话、任务和证据编排服务。
 *
 * @author mwangli
 * @since 2026-10-17
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentApplicationService {

    private final AgentSessionRepository sessionRepository;
    private final AgentTaskRepository taskRepository;
    private final AgentRuntimeService runtimeService;
    private final AgentProperties properties;
    private final JobDiagnosticTool jobDiagnosticTool;
    private final EvidenceAssembler evidenceAssembler;
    private final LangChainEmbeddingService embeddingService;

    /**
     * 创建 Agent 会话。
     *
     * @param request 创建请求
     * @param userScope 服务端认证用户范围
     * @return 会话响应
     */
    @Transactional
    public SessionResponse createSession(CreateSessionRequest request, String userScope) {
        LocalDateTime now = LocalDateTime.now();
        AgentSessionEntity entity = new AgentSessionEntity()
                .setSessionId(UUID.randomUUID().toString())
                .setUserScope(requireUserScope(userScope))
                .setTitle(request == null || !StringUtils.hasText(request.getTitle()) ? "Agent 会话" : request.getTitle().trim())
                .setStatus(AgentEnums.SessionStatus.ACTIVE)
                .setCreatedAt(now)
                .setUpdatedAt(now);
        return toSession(sessionRepository.save(entity));
    }

    /**
     * 查询 Agent 会话。
     *
     * @param sessionId 会话标识
     * @param userScope 服务端认证用户范围
     * @return 会话响应
     */
    @Transactional(readOnly = true)
    public SessionResponse getSession(String sessionId, String userScope) {
        return toSession(requireOwnedSession(sessionId, userScope));
    }

    /**
     * 为交易计划生成流程获取或创建会话。
     *
     * @param sessionId 会话标识
     * @param userScope 用户范围
     * @return 会话实体
     */
    @Transactional
    public AgentSessionEntity ensureSessionForTradePlan(String sessionId, String userScope) {
        return ensureSession(sessionId, userScope);
    }

    /**
     * 执行 Research 任务。
     *
     * @param request Research 请求
     * @param userScope 服务端认证用户范围
     * @param allowedTools 本轮允许模型调用的工具名称
     * @return 任务响应
     */
    public TaskResponse research(ResearchRequest request, String userScope, Set<AgentToolName> allowedTools) {
        requireEnabled();
        String question = requireQuestion(request == null ? null : request.getQuestion());
        AgentSessionEntity session = ensureSession(request == null ? null : request.getSessionId(), userScope);
        AgentTaskEntity task = createTask(session, AgentEnums.TaskType.RESEARCH, question);
        try {
            log.info("[Agent][Progress] taskId={} sessionId={} type={} progress=2/5 stage=CAPABILITY_READY allowedToolCount={}",
                    task.getTaskId(), task.getSessionId(), task.getTaskType(),
                    allowedTools == null ? 0 : allowedTools.size());
            EvidenceBundle factEvidence = EvidenceBundle.builder()
                    .summary("").items(List.of()).evidenceRefs(List.of()).build();
            String evidenceSummary = "";
            log.info("[Agent][Progress] taskId={} sessionId={} type={} progress=3/5 stage=EVIDENCE_READY evidenceCount={} evidenceRefCount={} summaryChars={}",
                    task.getTaskId(), task.getSessionId(), task.getTaskType(), factEvidence.getItems().size(),
                    factEvidence.getEvidenceRefs().size(), evidenceSummary.length());
            String prompt = "问题：" + question + "\n候选批次="
                    + (request == null ? "" : request.getCandidateBatchId())
                    + "\n预测批次=" + (request == null ? "" : request.getPredictionBatchId())
                    + "\n股票代码=" + (request == null ? "" : request.getStockCode())
                    + "\n策略版本=" + (request == null ? "" : request.getStrategyVersion())
                    + "\n目标交易日=" + (request == null ? "" : request.getTargetTradeDate())
                    + "\n结构化证据：" + evidenceSummary;
            log.info("[Agent][Progress] taskId={} sessionId={} type={} progress=4/5 stage=MODEL_CALL message=开始调用模型",
                    task.getTaskId(), task.getSessionId(), task.getTaskType());
            AgentRuntimeResponse response = runtimeService.execute(runtimeRequest(
                    task, session, "RESEARCH",
                    "你是股票交易系统的受限金融 Agent。可以在服务端授权的只读工具集合内按需查询；"
                            + "没有授权工具时按普通对话回答。不得承诺收益，不得生成可直接执行的券商订单。"
                            + "只返回合法的 Research JSON，不要输出 Markdown、标题或解释文字，字段为："
                            + "{summary,status,keyFindings,evidenceRefs,uncertainty,nextActions}。"
                            + "summary 和 uncertainty 各不超过 80 个汉字；keyFindings 最多 5 项，nextActions 最多 3 项，每项不超过 50 个汉字。"
                            + "所有自然语言字段使用中文；confirmed 只表示交易日历数据已确认，绝不表示交易已确认或已执行；"
                            + "tradingDay 才表示当天是否为交易日。"
                            + "status 只能是 PASS、WARN 或 UNKNOWN；evidenceRefs 只能填写本次证据编号："
                            + String.join(",", factEvidence.getEvidenceRefs()) + "。",
                    prompt, evidenceSummary, "RESEARCH", factEvidence.getEvidenceRefs(), allowedTools));
            finish(task, response);
        } catch (Exception exception) {
            fail(task, exception);
        }
        return toTask(taskRepository.save(task));
    }

    /**
     * 执行固定规则诊断任务。
     *
     * @param request 诊断请求
     * @param userScope 服务端认证用户范围
     * @param allowedTools 本轮允许模型调用的工具名称
     * @return 任务响应
     */
    public TaskResponse diagnose(DiagnosticRequest request, String userScope, Set<AgentToolName> allowedTools) {
        requireEnabled();
        String question = StringUtils.hasText(request == null ? null : request.getQuestion())
                ? request.getQuestion().trim() : "诊断系统运行状态";
        AgentSessionEntity session = ensureSession(request == null ? null : request.getSessionId(), userScope);
        AgentTaskEntity task = createTask(session, AgentEnums.TaskType.DIAGNOSTIC, question);
        String jobName = request == null ? null : request.getJobName();
        log.info("[Agent][Progress] taskId={} sessionId={} type={} progress=2/5 stage=EVIDENCE_LOADING jobName={}",
                task.getTaskId(), task.getSessionId(), task.getTaskType(), jobName);
        EvidenceBundle factEvidence = evidenceAssembler.assemble(AgentEnums.TaskType.DIAGNOSTIC,
                UUID.randomUUID().toString(), session.getSessionId(), task.getTaskId(), session.getUserScope(),
                null, jobName, null, question);
        String evidence = factEvidence.getSummary();
        try {
            log.info("[Agent][Progress] taskId={} sessionId={} type={} progress=3/5 stage=EVIDENCE_READY evidenceCount={} evidenceRefCount={} summaryChars={}",
                    task.getTaskId(), task.getSessionId(), task.getTaskType(), factEvidence.getItems().size(),
                    factEvidence.getEvidenceRefs().size(), evidence.length());
            if (isRuntimeDiagnostic(question)) {
                log.info("[Agent][Progress] taskId={} sessionId={} type={} progress=4/5 stage=DETERMINISTIC_RUNTIME_DIAGNOSTIC message=使用轻量 Provider 实测结果",
                        task.getTaskId(), task.getSessionId(), task.getTaskType());
                AgentRuntimeResponse response = runtimeDiagnosticResponse(factEvidence);
                finish(task, response);
                return toTask(taskRepository.save(task));
            }
            log.info("[Agent][Progress] taskId={} sessionId={} type={} progress=4/5 stage=MODEL_CALL message=开始调用模型",
                    task.getTaskId(), task.getSessionId(), task.getTaskType());
            AgentRuntimeResponse response = runtimeService.execute(runtimeRequest(
                    task, session, "DIAGNOSTIC",
                    "你是运行诊断 Agent。只根据结构化检查结果输出 PASS、WARN、BLOCKED 或 UNKNOWN，不要猜测。"
                            + "只返回 Diagnostic JSON：{summary,status,checks,blockedReasons,evidenceRefs,uncertainty}。",
                    "问题：" + question + "\n结构化检查结果：\n" + evidence,
                    evidence, "DIAGNOSTIC", factEvidence.getEvidenceRefs(), allowedTools));
            finish(task, response);
        } catch (Exception exception) {
            fail(task, exception);
        }
        return toTask(taskRepository.save(task));
    }

    private AgentRuntimeResponse runtimeDiagnosticResponse(EvidenceBundle evidence) {
        String provider = runtimeService.providerName();
        boolean available = switch (provider) {
            case "ollama", "deepseek" -> runtimeService.providerHealthCheck(provider);
            case "auto" -> {
                var health = runtimeService.providerHealthCheck();
                yield health.isDeepseekAvailable() || health.isOllamaAvailable();
            }
            default -> false;
        };
        var providerStatus = runtimeService.providerStatus();
        String model = "deepseek".equals(provider)
                ? properties.getDeepseek().getModel() : properties.getOllama().getModel();
        String currentStatus = "deepseek".equals(provider)
                ? providerStatus.getDeepseekStatus() : providerStatus.getOllamaStatus();
        String summary = available
                ? "当前 " + provider + " 模型连接正常，模型 " + model + " 已可用"
                : "当前 " + provider + " 模型连接不可用，请检查服务地址、模型是否存在及熔断状态";

        DiagnosticResult.DiagnosticCheck check = new DiagnosticResult.DiagnosticCheck();
        check.setName("Provider 连接与模型存在性");
        check.setStatus(available ? "PASS" : "BLOCKED");
        check.setDetail("provider=" + provider + "，model=" + model + "，runtimeStatus=" + currentStatus);
        check.setEvidenceRefs(evidence.getEvidenceRefs());

        DiagnosticResult result = new DiagnosticResult();
        result.setSummary(summary);
        result.setStatus(available ? "PASS" : "BLOCKED");
        result.setChecks(List.of(check));
        result.setBlockedReasons(available ? List.of() : List.of("Provider 健康检查未通过"));
        result.setEvidenceRefs(evidence.getEvidenceRefs());
        result.setUncertainty(available
                ? "连接检查不代表复杂问题的生成速度；本地 7B 模型速度仍受 CPU/GPU 资源影响"
                : "当前只确认 Provider 健康检查失败，具体底层原因需结合 Ollama 服务日志判断");
        return AgentRuntimeResponse.builder()
                .status(AgentRuntimeStatus.COMPLETED)
                .provider(provider)
                .model(model)
                .content(JSON.toJSONString(result))
                .evidenceSummary(evidence.getSummary())
                .structuredOutput(result)
                .build();
    }

    private boolean isRuntimeDiagnostic(String question) {
        if (!StringUtils.hasText(question)) {
            return false;
        }
        String normalized = question.toLowerCase();
        return normalized.contains("模型连接")
                || normalized.contains("模型状态")
                || normalized.contains("provider")
                || normalized.contains("ollama")
                || normalized.contains("deepseek")
                || normalized.contains("agent状态")
                || normalized.contains("agent 状态")
                || normalized.contains("响应超时")
                || normalized.contains("对话超时")
                || normalized.contains("model connection")
                || normalized.contains("provider health")
                || normalized.contains("timeout");
    }

    /**
     * 生成固定模板每日投研报告。
     *
     * @param request 报告请求
     * @param userScope 服务端认证用户范围
     * @return 任务响应
     */
    public TaskResponse dailyReport(ReportRequest request, String userScope) {
        requireEnabled();
        if (!properties.isReportEnabled()) {
            throw new IllegalStateException("日报任务当前未启用");
        }
        String reportDate = StringUtils.hasText(request == null ? null : request.getReportDate())
                ? request.getReportDate() : LocalDate.now().toString();
        AgentSessionEntity session = ensureSession(request == null ? null : request.getSessionId(), userScope);
        AgentTaskEntity task = createTask(session, AgentEnums.TaskType.REPORT, "生成每日投研报告: " + reportDate);
        try {
            log.info("[Agent][Progress] taskId={} sessionId={} type={} progress=2/5 stage=EVIDENCE_LOADING reportDate={}",
                    task.getTaskId(), task.getSessionId(), task.getTaskType(), reportDate);
            EvidenceBundle factEvidence = evidenceAssembler.assemble(AgentEnums.TaskType.REPORT,
                    UUID.randomUUID().toString(), session.getSessionId(), task.getTaskId(), session.getUserScope(), null, null);
            String template = "报告日期：" + reportDate + "\n"
                    + "市场概览：待结构化行情工具提供\n"
                    + "候选摘要：待策略排名工具提供\n"
                    + "模型信号：待模型预测工具提供\n"
                    + "情感信号：待情感工具提供\n"
                    + "风险提示：必须以 Java 风控结果为准\n"
                    + "数据质量：待任务和批次状态提供\n"
                    + "结构化事实证据：\n" + factEvidence.getSummary();
            log.info("[Agent][Progress] taskId={} sessionId={} type={} progress=3/5 stage=EVIDENCE_READY evidenceCount={} evidenceRefCount={} summaryChars={}",
                    task.getTaskId(), task.getSessionId(), task.getTaskType(), factEvidence.getItems().size(),
                    factEvidence.getEvidenceRefs().size(), template.length());
            log.info("[Agent][Progress] taskId={} sessionId={} type={} progress=4/5 stage=MODEL_CALL message=开始调用模型",
                    task.getTaskId(), task.getSessionId(), task.getTaskType());
            AgentRuntimeResponse response = runtimeService.execute(runtimeRequest(
                    task, session, "REPORT",
                    "你是投研报告 Agent。严格按照固定章节输出，不得补造行情、收益或交易结论。"
                            + "只返回 Report JSON：{reportDate,summary,status,sections,dataQuality,evidenceRefs,uncertainty}。sections 必须包含 marketOverview、candidateSummary、modelSignals、sentimentSignals、strategyStatus、riskWarnings、dataQuality、abnormalJobs。",
                    template, template, "REPORT", factEvidence.getEvidenceRefs(), Set.of()));
            finish(task, response);
        } catch (Exception exception) {
            fail(task, exception);
        }
        return toTask(taskRepository.save(task));
    }

    /**
     * 执行统一任务调度中心调用的日报 Job。
     */
    public void runScheduledReport() {
        ReportRequest request = new ReportRequest();
        request.setReportDate(LocalDate.now().toString());
        dailyReport(request, "system");
    }

    /**
     * 查询任务详情。
     *
     * @param taskId 任务标识
     * @param userScope 服务端认证用户范围
     * @return 任务响应
     */
    @Transactional(readOnly = true)
    public TaskResponse getTask(String taskId, String userScope) {
        AgentTaskEntity task = taskRepository.findById(taskId)
                .orElseThrow(() -> new IllegalArgumentException("Agent 任务不存在"));
        requireOwnedSession(task.getSessionId(), userScope);
        return toTask(task);
    }

    /**
     * 取消尚未完成的任务。
     *
     * @param taskId 任务标识
     * @param userScope 服务端认证用户范围
     */
    @Transactional
    public void cancel(String taskId, String userScope) {
        AgentTaskEntity task = taskRepository.findById(taskId)
                .orElseThrow(() -> new IllegalArgumentException("Agent 任务不存在"));
        requireOwnedSession(task.getSessionId(), userScope);
        if (task.getStatus() == AgentEnums.TaskStatus.COMPLETED || task.getStatus() == AgentEnums.TaskStatus.FAILED) {
            throw new IllegalStateException("任务已结束，不能取消: " + taskId);
        }
        task.setStatus(AgentEnums.TaskStatus.CANCELLED).setUpdatedAt(LocalDateTime.now());
        taskRepository.save(task);
    }

    /**
     * 获取 Agent 运行状态。
     *
     * @return 运行状态
     */
    @Transactional(readOnly = true)
    public RuntimeStatus runtimeStatus() {
        var providerStatus = runtimeService.providerStatus();
        EmbeddingRuntimeStatus embeddingStatus = embeddingService.status();
        return RuntimeStatus.builder()
                .enabled(properties.isEnabled())
                .provider(runtimeService.providerName())
                .ragEnabled(properties.isRagEnabled())
                .memoryEnabled(properties.isMemoryEnabled())
                .runningTasks(taskRepository.countByStatus(AgentEnums.TaskStatus.RUNNING))
                .checkedAt(LocalDateTime.now())
                .deepseekEnabled(providerStatus.isDeepseekEnabled())
                .deepseekConfigured(providerStatus.isDeepseekConfigured())
                .ollamaEnabled(providerStatus.isOllamaEnabled())
                .ollamaConfigured(providerStatus.isOllamaConfigured())
                .deepseekStatus(providerStatus.getDeepseekStatus())
                .deepseekModel(properties.getDeepseek().getModel())
                .ollamaStatus(providerStatus.getOllamaStatus())
                .ollamaModel(properties.getOllama().getModel())
                .deepseekCircuitState(providerStatus.getDeepseekCircuitState())
                .ollamaCircuitState(providerStatus.getOllamaCircuitState())
                .embeddingEnabled(embeddingStatus.isEnabled())
                .embeddingProvider(embeddingStatus.getProvider())
                .embeddingConfigured(embeddingStatus.isConfigured())
                .embeddingReady(embeddingStatus.isReady())
                .embeddingModel(embeddingStatus.getModel())
                .embeddingModelVersion(embeddingStatus.getModelVersion())
                .embeddingDimension(embeddingStatus.getDimension())
                .embeddingStatus(embeddingStatus.getStatus())
                .embeddingFallback(embeddingStatus.isFallback())
                .build();
    }

    /**
     * 执行 Agent Provider 健康检查。
     *
     * @return DeepSeek 和 Ollama 的实际可用状态
     */
    @Transactional(readOnly = true)
    public AgentHealthResponse healthCheck() {
        var health = runtimeService.providerHealthCheck();
        return AgentHealthResponse.builder()
                .deepseekAvailable(health.isDeepseekAvailable())
                .ollamaAvailable(health.isOllamaAvailable())
                .build();
    }

    /**
     * 切换 Agent 运行时 Provider；指定 Provider 必须先通过健康检查。
     *
     * @param request Provider 切换请求
     * @return 当前 Provider 模式
     */
    public ProviderModeResponse switchProvider(ProviderModeRequest request) {
        String provider = request == null ? null : request.getProvider();
        if ("deepseek".equalsIgnoreCase(provider) || "ollama".equalsIgnoreCase(provider)) {
            boolean available = runtimeService.providerHealthCheck(provider.toLowerCase());
            if (!available) {
                throw new IllegalStateException("目标 Provider 当前不可用: " + provider);
            }
        }
        runtimeService.switchProvider(provider);
        return ProviderModeResponse.builder().provider(runtimeService.providerName()).build();
    }

    /**
     * 判断交易计划能力是否启用，供生成入口提前阻断无效模型调用。
     *
     * @return 交易计划开关状态
     */
    public boolean isTradePlanEnabled() {
        return properties.isTradePlanEnabled();
    }

    private AgentSessionEntity ensureSession(String sessionId, String userScope) {
        if (StringUtils.hasText(sessionId)) {
            return requireOwnedSession(sessionId, userScope);
        }
        CreateSessionRequest request = new CreateSessionRequest();
        request.setTitle("Agent 自动会话");
        SessionResponse response = createSession(request, userScope);
        return requireOwnedSession(response.getSessionId(), userScope);
    }

    private AgentTaskEntity createTask(AgentSessionEntity session, AgentEnums.TaskType type, String question) {
        LocalDateTime now = LocalDateTime.now();
        AgentTaskEntity task = taskRepository.save(new AgentTaskEntity()
                .setTaskId(UUID.randomUUID().toString())
                .setSessionId(session.getSessionId())
                .setTaskType(type)
                .setStatus(AgentEnums.TaskStatus.RUNNING)
                .setQuestion(question)
                .setCreatedAt(now)
                .setUpdatedAt(now));
        log.info("[Agent][Progress] taskId={} sessionId={} type={} progress=1/5 stage=TASK_CREATED questionLength={}",
                task.getTaskId(), task.getSessionId(), task.getTaskType(), question == null ? 0 : question.length());
        return task;
    }

    private void finish(AgentTaskEntity task, AgentRuntimeResponse response) {
        AgentEnums.TaskStatus status;
        if (response.getStatus() == AgentRuntimeStatus.FAILED) {
            status = AgentEnums.TaskStatus.FAILED;
        } else if (response.getStatus() == AgentRuntimeStatus.DEGRADED) {
            status = AgentEnums.TaskStatus.DEGRADED;
        } else {
            status = AgentEnums.TaskStatus.COMPLETED;
        }
        AgentStructuredOutput structured = response.getStructuredOutput();
        String resultSummary = structured == null ? response.getContent() : structured.getSummary();
        String evidenceSummary = response.getEvidenceSummary();
        String evidenceRefs = structured == null || structured.getEvidenceRefs() == null
                ? null : JSON.toJSONString(structured.getEvidenceRefs());
        task.setStatus(status)
                .setResultType(task.getTaskType().name())
                .setResultSummary(limit(resultSummary))
                .setStructuredResult(structured == null ? null : limit(JSON.toJSONString(structured)))
                .setEvidenceSummary(limit(evidenceSummary))
                .setEvidenceRefs(evidenceRefs)
                .setUncertainty(structured == null ? null : limit(structured.getUncertainty()))
                .setErrorMessage(response.getErrorCode())
                .setUpdatedAt(LocalDateTime.now());
        log.info("[Agent][Progress] taskId={} sessionId={} type={} progress=5/5 stage=TASK_FINISHED status={} provider={} model={} errorCode={} elapsedMs={}",
                task.getTaskId(), task.getSessionId(), task.getTaskType(), task.getStatus(), response.getProvider(),
                response.getModel(), response.getErrorCode(), elapsedMillis(task));
    }

    private AgentRuntimeRequest runtimeRequest(AgentTaskEntity task, AgentSessionEntity session,
                                               String taskType, String systemPrompt, String userPrompt,
                                               String evidenceSummary, String outputType,
                                               List<String> evidenceRefs, Set<AgentToolName> allowedTools) {
        return AgentRuntimeRequest.builder()
                .taskType(taskType)
                .sessionId(session.getSessionId())
                .taskId(task.getTaskId())
                .userScope(session.getUserScope())
                .systemPrompt(systemPrompt)
                .userPrompt(userPrompt)
                .evidenceSummary(evidenceSummary)
                .evidenceRefs(evidenceRefs)
                .allowedTools(allowedTools == null ? Set.of() : Set.copyOf(allowedTools))
                .outputType(outputType)
                .build();
    }

    private AgentSessionEntity requireOwnedSession(String sessionId, String userScope) {
        if (!StringUtils.hasText(sessionId)) {
            throw new IllegalArgumentException("Agent 会话不存在");
        }
        return sessionRepository.findBySessionIdAndUserScope(sessionId.trim(), requireUserScope(userScope))
                .orElseThrow(() -> new IllegalArgumentException("Agent 会话不存在"));
    }

    private String requireUserScope(String userScope) {
        if (!StringUtils.hasText(userScope)) {
            throw new IllegalArgumentException("Agent 用户范围不能为空");
        }
        return userScope.trim();
    }

    private String queryJobEvidence(AgentTaskEntity task, AgentSessionEntity session, String jobName) {
        ToolExecutionContext context = ToolExecutionContext.builder()
                .traceId(UUID.randomUUID().toString())
                .sessionId(session.getSessionId())
                .taskId(task.getTaskId())
                .userScope(session.getUserScope())
                .allowedToolNames(Set.of("queryJobDiagnostic"))
                .build();
        AgentToolContextHolder.set(context);
        try {
            JobDiagnosticTool.JobDiagnosticResult diagnostic = jobDiagnosticTool.query(
                    new JobDiagnosticTool.JobDiagnosticRequest(jobName.trim()));
            return "任务=" + diagnostic.jobName() + "，最近运行=" + diagnostic.status()
                    + "，错误摘要=" + (diagnostic.errorMessage() == null ? "无" : diagnostic.errorMessage()) + "\n";
        } catch (RuntimeException exception) {
            return "任务=" + jobName.trim() + "，工具状态=FAILED，错误码=TOOL_EXECUTION_FAILED，说明="
                    + exception.getMessage() + "\n";
        } finally {
            AgentToolContextHolder.clear();
        }
    }

    private void fail(AgentTaskEntity task, Exception exception) {
        task.setStatus(AgentEnums.TaskStatus.FAILED)
                .setErrorMessage(limit(exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage()))
                .setUpdatedAt(LocalDateTime.now());
        log.warn("[Agent][Progress] taskId={} sessionId={} type={} stage=TASK_FAILED status={} causeType={} causeMessage={} elapsedMs={}",
                task.getTaskId(), task.getSessionId(), task.getTaskType(), task.getStatus(),
                exception.getClass().getSimpleName(), safeErrorMessage(exception.getMessage()), elapsedMillis(task));
    }

    private long elapsedMillis(AgentTaskEntity task) {
        if (task.getCreatedAt() == null || task.getUpdatedAt() == null) {
            return -1L;
        }
        return Duration.between(task.getCreatedAt(), task.getUpdatedAt()).toMillis();
    }

    private long elapsedMillis(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000L;
    }

    private String safeErrorMessage(String message) {
        if (!StringUtils.hasText(message)) {
            return "";
        }
        String sanitized = message
                .replaceAll("(?i)(authorization\\s*:\\s*bearer\\s+|api[-_ ]?key\\s*[=:]\\s*)[^\\s,;]+", "$1***")
                .replaceAll("(?i)sk-[a-z0-9_-]+", "sk-***");
        return sanitized.substring(0, Math.min(sanitized.length(), 300));
    }

    private TaskResponse toTask(AgentTaskEntity entity) {
        return TaskResponse.builder()
                .taskId(entity.getTaskId())
                .sessionId(entity.getSessionId())
                .taskType(entity.getTaskType().name())
                .status(entity.getStatus().name())
                .question(entity.getQuestion())
                .resultSummary(entity.getResultSummary())
                .resultType(entity.getResultType())
                .structuredResult(entity.getStructuredResult())
                .evidenceSummary(entity.getEvidenceSummary())
                .evidenceRefs(entity.getEvidenceRefs())
                .uncertainty(entity.getUncertainty())
                .errorMessage(entity.getErrorMessage())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    private SessionResponse toSession(AgentSessionEntity entity) {
        return SessionResponse.builder()
                .sessionId(entity.getSessionId())
                .title(entity.getTitle())
                .status(entity.getStatus().name())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    private String requireQuestion(String question) {
        if (!StringUtils.hasText(question)) {
            throw new IllegalArgumentException("Agent 问题不能为空");
        }
        if (question.length() > 2000) {
            throw new IllegalArgumentException("Agent 问题不能超过 2000 个字符");
        }
        return question.trim();
    }

    private void requireEnabled() {
        if (!properties.isEnabled()) {
            throw new IllegalStateException("Agent 功能当前未启用");
        }
    }

    private String limit(String value) {
        if (value == null) {
            return null;
        }
        return value.length() > properties.getMaxOutputChars()
                ? value.substring(0, properties.getMaxOutputChars()) : value;
    }
}
// AI_GENERATE_END ---------------------------------
