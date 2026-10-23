// AI_GENERATE_START------------------------------------------
package com.stock.agent.service;

import com.alibaba.fastjson2.JSON;
import com.stock.agent.capability.AgentToolName;
import com.stock.agent.config.AgentProperties;
import com.stock.agent.domain.AgentEnums;
import com.stock.agent.dto.DiagnosticRequest;
import com.stock.agent.dto.ReportRequest;
import com.stock.agent.dto.ResearchRequest;
import com.stock.agent.dto.TaskResponse;
import com.stock.agent.entity.AgentSessionEntity;
import com.stock.agent.entity.AgentTaskEntity;
import com.stock.agent.evidence.EvidenceAssembler;
import com.stock.agent.evidence.EvidenceBundle;
import com.stock.agent.runtime.AgentRuntimeRequest;
import com.stock.agent.runtime.AgentRuntimeResponse;
import com.stock.agent.runtime.AgentRuntimeCoordinator;
import com.stock.agent.runtime.AgentRuntimeStatus;
import com.stock.agent.runtime.DiagnosticResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Agent 研究、诊断和报告任务编排服务。
 *
 * 负责准备证据、构造 Runtime 请求并驱动任务状态，不承担会话或任务仓储职责。
 *
 * @author mwangli
 * @since 2026-10-17
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentApplicationService {

    private final AgentRuntimeCoordinator runtimeService;
    private final AgentProperties properties;
    private final EvidenceAssembler evidenceAssembler;
    private final AgentSessionService sessionService;
    private final AgentTaskService taskService;

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
        AgentSessionEntity session = sessionService.ensure(request == null ? null : request.getSessionId(), userScope);
        AgentTaskEntity task = taskService.create(session, AgentEnums.TaskType.RESEARCH, question);
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
            return taskService.complete(task, response);
        } catch (Exception exception) {
            return taskService.fail(task, exception);
        }
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
        AgentSessionEntity session = sessionService.ensure(request == null ? null : request.getSessionId(), userScope);
        AgentTaskEntity task = taskService.create(session, AgentEnums.TaskType.DIAGNOSTIC, question);
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
                return taskService.complete(task, runtimeDiagnosticResponse(factEvidence));
            }
            log.info("[Agent][Progress] taskId={} sessionId={} type={} progress=4/5 stage=MODEL_CALL message=开始调用模型",
                    task.getTaskId(), task.getSessionId(), task.getTaskType());
            AgentRuntimeResponse response = runtimeService.execute(runtimeRequest(
                    task, session, "DIAGNOSTIC",
                    "你是运行诊断 Agent。只根据结构化检查结果输出 PASS、WARN、BLOCKED 或 UNKNOWN，不要猜测。"
                            + "只返回 Diagnostic JSON：{summary,status,checks,blockedReasons,evidenceRefs,uncertainty}。",
                    "问题：" + question + "\n结构化检查结果：\n" + evidence,
                    evidence, "DIAGNOSTIC", factEvidence.getEvidenceRefs(), allowedTools));
            return taskService.complete(task, response);
        } catch (Exception exception) {
            return taskService.fail(task, exception);
        }
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
        AgentSessionEntity session = sessionService.ensure(request == null ? null : request.getSessionId(), userScope);
        AgentTaskEntity task = taskService.create(session, AgentEnums.TaskType.REPORT, "生成每日投研报告: " + reportDate);
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
                            + "只返回 Report JSON：{reportDate,summary,status,sections,dataQuality,evidenceRefs,uncertainty}。"
                            + "sections 必须包含 marketOverview、candidateSummary、modelSignals、sentimentSignals、strategyStatus、riskWarnings、dataQuality、abnormalJobs。",
                    template, template, "REPORT", factEvidence.getEvidenceRefs(), Set.of()));
            return taskService.complete(task, response);
        } catch (Exception exception) {
            return taskService.fail(task, exception);
        }
    }

    /**
     * 执行统一任务调度中心调用的日报任务。
     */
    public void runScheduledReport() {
        ReportRequest request = new ReportRequest();
        request.setReportDate(LocalDate.now().toString());
        dailyReport(request, "system");
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
}
// AI_GENERATE_END------------------------------------------
