// AI_GENERATE_START ------
package com.stock.strategyAgent.evidence;

import com.alibaba.fastjson2.JSON;
import com.stock.strategyAgent.domain.AgentEnums;
import com.stock.strategyAgent.tool.AgentToolContextHolder;
import com.stock.strategyAgent.tool.ToolExecutionContext;
import com.stock.strategyAgent.tool.read.JobDiagnosticTool;
import com.stock.strategyAgent.tool.read.PredictionCandidateQueryTool;
import com.stock.strategyAgent.tool.read.TradingCalendarQueryTool;
import com.stock.strategyAgent.tool.read.BrokerFactQueryTool;
import com.stock.strategyAgent.tool.read.StrategyStateQueryTool;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Agent 受限证据集合组装服务。
 *
 * @author mwangli
 * @since 2026-10-17
 */
@Service
@RequiredArgsConstructor
public class EvidenceAssembler {

    private final TradingCalendarQueryTool tradingCalendarQueryTool;
    private final StrategyStateQueryTool strategyStateQueryTool;
    private final PredictionCandidateQueryTool predictionCandidateQueryTool;
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
    public EvidenceBundle assemble(AgentEnums.TaskType taskType, String traceId, String sessionId,
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
    public EvidenceBundle assemble(AgentEnums.TaskType taskType, String traceId, String sessionId,
                                   String taskId, String userScope, String candidateBatchId, String jobName,
                                   LocalDate requestedTradeDate) {
        ToolExecutionContext context = ToolExecutionContext.builder()
                .traceId(traceId)
                .sessionId(sessionId)
                .taskId(taskId)
                .userScope(userScope)
                .build();
        List<EvidenceItem> items = new ArrayList<>();
        AgentToolContextHolder.set(context);
        try {
            LocalDate calendarTradeDate = resolveTargetTradeDate(requestedTradeDate);
            add(items, "queryTradingCalendar",
                    () -> tradingCalendarQueryTool.query(new TradingCalendarQueryTool.TradingCalendarRequest(calendarTradeDate)));
            add(items, "queryStrategyState", strategyStateQueryTool::query);
            LocalDate targetTradeDate = calendarTradeDate;
            String predictionBatchId = null;
            String strategyVersion = null;
            if (StringUtils.hasText(candidateBatchId)) {
                PredictionCandidateQueryTool.PredictionCandidateResult candidateData = add(items,
                        "queryPredictionCandidate",
                        () -> predictionCandidateQueryTool.query(
                                new PredictionCandidateQueryTool.PredictionCandidateRequest(candidateBatchId.trim())));
                if (candidateData != null) {
                    predictionBatchId = candidateData.predictionBatchId();
                    strategyVersion = candidateData.strategyVersion();
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
                    strategyVersion = candidateData.strategyVersion();
                    targetTradeDate = candidateData.targetTradeDate() == null
                            ? targetTradeDate : candidateData.targetTradeDate();
                }
            }
            if (taskType == AgentEnums.TaskType.DIAGNOSTIC && StringUtils.hasText(jobName)) {
                add(items, "queryJobExecution",
                        () -> jobDiagnosticTool.query(new JobDiagnosticTool.JobDiagnosticRequest(jobName.trim())));
            }
            if (taskType == AgentEnums.TaskType.TRADE_PLAN || taskType == AgentEnums.TaskType.DIAGNOSTIC) {
                add(items, "queryBrokerFacts", brokerFactQueryTool::query);
            }
            List<String> refs = items.stream()
                    .filter(item -> "AVAILABLE".equals(item.getStatus()))
                    .map(EvidenceItem::getEvidenceId)
                    .toList();
            String summary = items.stream().map(item -> JSON.toJSONString(item)).reduce("", (left, right) -> left + right + "\n");
            return EvidenceBundle.builder().items(List.copyOf(items)).evidenceRefs(refs).summary(limit(summary))
                    .predictionBatchId(predictionBatchId)
                    .strategyVersion(strategyVersion)
                    .targetTradeDate(targetTradeDate)
                    .build();
        } finally {
            AgentToolContextHolder.clear();
        }
    }

    private LocalDate resolveTargetTradeDate(LocalDate requestedTradeDate) {
        LocalDate baseDate = requestedTradeDate == null ? LocalDate.now() : requestedTradeDate;
        return tradingCalendarQueryTool.nextConfirmedTradingDay(baseDate);
    }

    private <T> T add(List<EvidenceItem> items, String toolName, Supplier<T> action) {
        String id = "E" + String.format("%03d", items.size() + 1);
        try {
            T data = action.get();
            String status = "AVAILABLE";
            if (data instanceof TradingCalendarQueryTool.TradingCalendarResult calendar
                    && !calendar.confirmed()) {
                status = "UNKNOWN";
            }
            if (data instanceof PredictionCandidateQueryTool.PredictionCandidateResult candidate
                    && (candidate.items() == null || candidate.items().isEmpty())) {
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
            return data;
        } catch (RuntimeException exception) {
            items.add(EvidenceItem.builder()
                    .evidenceId(id)
                    .sourceType("TOOL")
                    .sourceId(toolName)
                    .sourceVersion("v1")
                    .asOf(LocalDateTime.now())
                    .status("FAILED")
                    .summary(limit(exception.getMessage()))
                    .errorCode("TOOL_EXECUTION_FAILED")
                    .build());
            return null;
        }
    }

    private String limit(String value) {
        if (value == null) {
            return "";
        }
        return value.length() > 12000 ? value.substring(0, 12000) : value;
    }
}
// AI_GENERATE_END -------
