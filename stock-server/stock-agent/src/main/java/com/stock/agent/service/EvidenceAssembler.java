// AI_GENERATE_START---------------------------
package com.stock.agent.service;

import com.stock.agent.enums.AgentTaskType;

import com.alibaba.fastjson2.JSON;
import com.stock.agent.model.EvidenceBundle;
import com.stock.agent.model.EvidenceItem;
import com.stock.agent.support.FactSourceUnavailableException;
import com.stock.agent.tool.AgentToolContextHolder;
import com.stock.agent.model.ToolExecutionContext;
import com.stock.agent.tool.JobDiagnosticTool;
import com.stock.agent.tool.PredictionCandidateQueryTool;
import com.stock.agent.tool.ProviderRuntimeQueryTool;
import com.stock.agent.tool.TradingCalendarQueryTool;
import com.stock.agent.tool.BrokerFactQueryTool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Agent 受限证据集合组装服务。
 *
 * @author mwangli
 * @since 2026-10-17
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class EvidenceAssembler {

    private final TradingCalendarQueryTool tradingCalendarQueryTool;
    private final PredictionCandidateQueryTool predictionCandidateQueryTool;
    private final ProviderRuntimeQueryTool providerRuntimeQueryTool;
    private final JobDiagnosticTool jobDiagnosticTool;
    private final BrokerFactQueryTool brokerFactQueryTool;

    /**
     * 按任务类型组装固定只读证据。
     *
     * @param taskType 任务类型
     * @param traceId 链路标识
     * @param sessionId 会话标识
     * @param taskId 任务标识
     * @param userScope 用户范围
     * @param candidateBatchId 候选批次，可为空
     * @param jobName 任务名称，可为空
     * @return 受限证据集合
     */
    public EvidenceBundle assemble(AgentTaskType taskType, String traceId, String sessionId,
                                   String taskId, String userScope, String candidateBatchId, String jobName) {
        return assemble(taskType, traceId, sessionId, taskId, userScope, candidateBatchId, jobName, null);
    }

    /**
     * 按指定目标交易日组装固定只读证据。
     *
     * @param taskType 任务类型
     * @param traceId 链路标识
     * @param sessionId 会话标识
     * @param taskId 任务标识
     * @param userScope 用户范围
     * @param candidateBatchId 候选批次，可为空
     * @param jobName 任务名称，可为空
     * @param requestedTradeDate 请求指定的目标交易日，可为空
     * @return 受限证据集合
     */
    public EvidenceBundle assemble(AgentTaskType taskType, String traceId, String sessionId,
                                   String taskId, String userScope, String candidateBatchId, String jobName,
                                   LocalDate requestedTradeDate) {
        return assemble(taskType, traceId, sessionId, taskId, userScope, candidateBatchId, jobName,
                requestedTradeDate, null);
    }

    /**
     * 按诊断问题裁剪固定证据，避免无关的实时券商查询拖慢本地模型响应。
     *
     * @param taskType 任务类型
     * @param traceId 链路标识
     * @param sessionId 会话标识
     * @param taskId 任务标识
     * @param userScope 用户范围
     * @param candidateBatchId 候选批次，可为空
     * @param jobName 任务名称，可为空
     * @param requestedTradeDate 请求指定的目标交易日，可为空
     * @param diagnosticQuestion 诊断问题，可为空
     * @return 受限证据集合
     */
    public EvidenceBundle assemble(AgentTaskType taskType, String traceId, String sessionId,
                                   String taskId, String userScope, String candidateBatchId, String jobName,
                                   LocalDate requestedTradeDate, String diagnosticQuestion) {
        long startedAt = System.nanoTime();
        ToolExecutionContext context = ToolExecutionContext.builder()
                .traceId(traceId)
                .sessionId(sessionId)
                .taskId(taskId)
                .userScope(userScope)
                .allowedToolNames(Set.of("queryProviderRuntime", "queryTradingCalendar",
                        "queryPredictionCandidates", "queryLatestPredictionCandidate",
                        "queryJobDiagnostic", "queryBrokerFacts"))
                .build();
        List<EvidenceItem> items = new ArrayList<>();
        boolean runtimeOnly = isRuntimeDiagnostic(taskType, diagnosticQuestion);
        boolean brokerFactsRequired = requiresBrokerFacts(taskType, diagnosticQuestion);
        log.info("[Agent][Evidence] taskId={} sessionId={} type={} status=STARTED candidateBatchId={} jobName={} requestedTradeDate={} runtimeOnly={} brokerFactsRequired={}",
                taskId, sessionId, taskType, candidateBatchId, jobName, requestedTradeDate, runtimeOnly,
                brokerFactsRequired);
        AgentToolContextHolder.set(context);
        try {
            if (runtimeOnly) {
                add(items, "queryProviderRuntime", providerRuntimeQueryTool::query);
                return buildBundle(taskType, taskId, sessionId, items, null, null, null, startedAt);
            }
            LocalDate baseTradeDate = requestedTradeDate == null ? LocalDate.now() : requestedTradeDate;
            TradingCalendarQueryTool.TradingCalendarResult calendar = add(items, "queryTradingCalendar",
                    () -> tradingCalendarQueryTool.query(
                            new TradingCalendarQueryTool.TradingCalendarRequest(baseTradeDate)));
            LocalDate calendarTradeDate = calendar == null ? null : calendar.nextConfirmedTradingDay();
            if (calendarTradeDate == null) {
                add(items, "queryProviderRuntime", providerRuntimeQueryTool::query);
                return buildBundle(taskType, taskId, sessionId, items, null, null, null, startedAt);
            }
            LocalDate targetTradeDate = calendarTradeDate;
            String predictionBatchId = null;
            String modelVersion = null;
            if (StringUtils.hasText(candidateBatchId)) {
                PredictionCandidateQueryTool.PredictionCandidateResult candidateData = add(items,
                        "queryPredictionCandidates",
                        () -> predictionCandidateQueryTool.query(
                                new PredictionCandidateQueryTool.PredictionCandidateRequest(candidateBatchId.trim())));
                if (candidateData != null) {
                    predictionBatchId = candidateData.predictionBatchId();
                    modelVersion = candidateData.modelVersion();
                    targetTradeDate = candidateData.targetTradeDate() == null
                            ? targetTradeDate : candidateData.targetTradeDate();
                }
            } else {
                LocalDate lookupTradeDate = targetTradeDate;
                PredictionCandidateQueryTool.PredictionCandidateResult candidateData = add(items,
                        "queryLatestPredictionCandidate",
                        () -> predictionCandidateQueryTool.queryLatest(lookupTradeDate));
                if (candidateData != null) {
                    predictionBatchId = candidateData.predictionBatchId();
                    modelVersion = candidateData.modelVersion();
                    targetTradeDate = candidateData.targetTradeDate() == null
                            ? targetTradeDate : candidateData.targetTradeDate();
                }
            }
            if (taskType == AgentTaskType.DIAGNOSTIC && StringUtils.hasText(jobName)) {
                add(items, "queryJobDiagnostic",
                        () -> jobDiagnosticTool.query(new JobDiagnosticTool.JobDiagnosticRequest(jobName.trim())));
            }
            if (brokerFactsRequired) {
                add(items, "queryBrokerFacts", brokerFactQueryTool::query);
            }
            add(items, "queryProviderRuntime", providerRuntimeQueryTool::query);
            return buildBundle(taskType, taskId, sessionId, items, predictionBatchId, modelVersion,
                    targetTradeDate, startedAt);
        } finally {
            AgentToolContextHolder.clear();
        }
    }

    private EvidenceBundle buildBundle(AgentTaskType taskType, String taskId, String sessionId,
                                       List<EvidenceItem> items, String predictionBatchId, String modelVersion,
                                       LocalDate targetTradeDate, long startedAt) {
        List<String> refs = items.stream()
                .filter(item -> "AVAILABLE".equals(item.getStatus()))
                .map(EvidenceItem::getEvidenceId)
                .toList();
        String summary = items.stream().map(item -> JSON.toJSONString(item))
                .reduce("", (left, right) -> left + right + "\n");
        long availableCount = items.stream().filter(item -> "AVAILABLE".equals(item.getStatus())).count();
        long failedCount = items.stream().filter(item -> "FAILED".equals(item.getStatus())).count();
        log.info("[Agent][Evidence] taskId={} sessionId={} type={} status=COMPLETED itemCount={} availableCount={} failedCount={} evidenceRefCount={} summaryChars={} elapsedMs={}",
                taskId, sessionId, taskType, items.size(), availableCount, failedCount, refs.size(),
                summary.length(), elapsedMillis(startedAt));
        return EvidenceBundle.builder().items(List.copyOf(items)).evidenceRefs(refs).summary(limit(summary))
                .predictionBatchId(predictionBatchId)
                .modelVersion(modelVersion)
                .targetTradeDate(targetTradeDate)
                .build();
    }

    private boolean isRuntimeDiagnostic(AgentTaskType taskType, String diagnosticQuestion) {
        if (taskType != AgentTaskType.DIAGNOSTIC || !StringUtils.hasText(diagnosticQuestion)) {
            return false;
        }
        String normalized = diagnosticQuestion.toLowerCase();
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

    private boolean requiresBrokerFacts(AgentTaskType taskType, String diagnosticQuestion) {
        if (taskType == AgentTaskType.TRADE_PLAN) {
            return true;
        }
        if (taskType != AgentTaskType.DIAGNOSTIC) {
            return false;
        }
        if (!StringUtils.hasText(diagnosticQuestion)) {
            return true;
        }
        String normalized = diagnosticQuestion.toLowerCase();
        return normalized.contains("资金")
                || normalized.contains("账户")
                || normalized.contains("持仓")
                || normalized.contains("委托")
                || normalized.contains("订单")
                || normalized.contains("成交")
                || normalized.contains("余额")
                || normalized.contains("broker")
                || normalized.contains("account")
                || normalized.contains("position")
                || normalized.contains("order")
                || normalized.contains("fill");
    }

    private boolean requiresTradeGate(AgentTaskType taskType, String diagnosticQuestion) {
        if (taskType != AgentTaskType.DIAGNOSTIC || !StringUtils.hasText(diagnosticQuestion)) {
            return false;
        }
        String normalized = diagnosticQuestion.toLowerCase();
        return normalized.contains("买入")
                || normalized.contains("卖出")
                || normalized.contains("下单")
                || normalized.contains("交易执行")
                || normalized.contains("执行门禁")
                || normalized.contains("未成交")
                || normalized.contains("order")
                || normalized.contains("trade gate");
    }

    private <T> T add(List<EvidenceItem> items, String toolName, Supplier<T> action) {
        String id = "E" + String.format("%03d", items.size() + 1);
        ToolExecutionContext context = AgentToolContextHolder.current();
        long startedAt = System.nanoTime();
        log.info("[Agent][Tool] taskId={} sessionId={} evidenceId={} tool={} status=STARTED",
                context == null ? null : context.getTaskId(), context == null ? null : context.getSessionId(), id, toolName);
        try {
            T data = action.get();
            String status = "AVAILABLE";
            if (data instanceof TradingCalendarQueryTool.TradingCalendarResult calendar
                    && (!calendar.confirmed() || calendar.nextConfirmedTradingDay() == null)) {
                status = "UNKNOWN";
            }
            items.add(EvidenceItem.builder()
                    .evidenceId(id)
                    .sourceType("TOOL")
                    .sourceId(toolName)
                    .sourceVersion("v1")
                    .asOf(LocalDateTime.now())
                    .status(status)
                    .summary(limit(String.valueOf(data)))
                    .build());
            log.info("[Agent][Tool] taskId={} sessionId={} evidenceId={} tool={} status={} elapsedMs={}",
                    context == null ? null : context.getTaskId(), context == null ? null : context.getSessionId(),
                    id, toolName, status, elapsedMillis(startedAt));
            return data;
        } catch (RuntimeException exception) {
            FactSourceUnavailableException factSourceException = findFactSourceException(exception);
            String errorCode = factSourceException == null
                    ? "TOOL_EXECUTION_FAILED" : FactSourceUnavailableException.ERROR_CODE;
            items.add(EvidenceItem.builder()
                    .evidenceId(id)
                    .sourceType("TOOL")
                    .sourceId(toolName)
                    .sourceVersion("v1")
                    .asOf(LocalDateTime.now())
                    .status("FAILED")
                    .summary(limit(exception.getMessage()))
                    .errorCode(errorCode)
                    .build());
            log.warn("[Agent][Tool] taskId={} sessionId={} evidenceId={} tool={} status=FAILED causeType={} causeMessage={} elapsedMs={}",
                    context == null ? null : context.getTaskId(), context == null ? null : context.getSessionId(),
                    id, toolName, exception.getClass().getSimpleName(), safeErrorMessage(exception.getMessage()),
                    elapsedMillis(startedAt));
            if (factSourceException != null) {
                throw factSourceException;
            }
            return null;
        }
    }

    private FactSourceUnavailableException findFactSourceException(Throwable throwable) {
        Throwable current = throwable;
        while (current != null && current.getCause() != current) {
            if (current instanceof FactSourceUnavailableException factSourceException) {
                return factSourceException;
            }
            current = current.getCause();
        }
        return null;
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

    private String limit(String value) {
        if (value == null) {
            return "";
        }
        return value.length() > 12000 ? value.substring(0, 12000) : value;
    }
}
// AI_GENERATE_END---------------------------
