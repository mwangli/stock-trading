// AI_GENERATE_START --
package com.stock.strategyAgent.evidence;

import com.alibaba.fastjson2.JSON;
import com.stock.strategyAgent.domain.AgentEnums;
import com.stock.strategyAgent.tool.ToolExecutionContext;
import com.stock.strategyAgent.tool.ToolExecutionResult;
import com.stock.strategyAgent.tool.ToolGateway;
import com.stock.strategyAgent.tool.read.JobDiagnosticTool;
import com.stock.strategyAgent.tool.read.PredictionCandidateQueryTool;
import com.stock.strategyAgent.tool.read.TradingCalendarQueryTool;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Agent 受限证据集合组装服务。
 *
 * @author mwangli
 * @since 2026-10-17
 */
@Service
@RequiredArgsConstructor
public class EvidenceAssembler {

    private final ToolGateway toolGateway;

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
        ToolExecutionContext context = ToolExecutionContext.builder()
                .traceId(traceId)
                .sessionId(sessionId)
                .taskId(taskId)
                .userScope(userScope)
                .build();
        List<EvidenceItem> items = new ArrayList<>();
        add(items, "queryTradingCalendar", new TradingCalendarQueryTool.TradingCalendarRequest(LocalDate.now()), context);
        add(items, "queryStrategyState", null, context);
        String predictionBatchId = null;
        String strategyVersion = null;
        LocalDate targetTradeDate = null;
        if (StringUtils.hasText(candidateBatchId)) {
            Object candidateData = add(items, "queryPredictionCandidate",
                    new PredictionCandidateQueryTool.PredictionCandidateRequest(candidateBatchId.trim()), context);
            if (candidateData instanceof PredictionCandidateQueryTool.PredictionCandidateResult candidate) {
                predictionBatchId = candidate.predictionBatchId();
                strategyVersion = candidate.strategyVersion();
                targetTradeDate = candidate.targetTradeDate();
            }
        }
        if (taskType == AgentEnums.TaskType.DIAGNOSTIC && StringUtils.hasText(jobName)) {
            add(items, "queryJobExecution", new JobDiagnosticTool.JobDiagnosticRequest(jobName.trim()), context);
        }
        if (taskType == AgentEnums.TaskType.TRADE_PLAN || taskType == AgentEnums.TaskType.DIAGNOSTIC) {
            add(items, "queryBrokerFacts", null, context);
        }
        List<String> refs = items.stream().map(EvidenceItem::getEvidenceId).toList();
        String summary = items.stream().map(item -> JSON.toJSONString(item)).reduce("", (left, right) -> left + right + "\n");
        return EvidenceBundle.builder().items(List.copyOf(items)).evidenceRefs(refs).summary(limit(summary))
                .predictionBatchId(predictionBatchId)
                .strategyVersion(strategyVersion)
                .targetTradeDate(targetTradeDate)
                .build();
    }

    private Object add(List<EvidenceItem> items, String toolName, Object input, ToolExecutionContext context) {
        ToolExecutionResult<Object> result = toolGateway.execute(toolName, input, context);
        String id = "E" + String.format("%03d", items.size() + 1);
        String status = result.isSuccess() ? "AVAILABLE" : "FAILED";
        String summary = result.isSuccess() ? limit(String.valueOf(result.getData())) : result.getWarnings();
        if (result.getData() instanceof TradingCalendarQueryTool.TradingCalendarResult calendar
                && !calendar.confirmed()) {
            status = "UNKNOWN";
        }
        if (result.getData() instanceof PredictionCandidateQueryTool.PredictionCandidateResult candidate
                && (candidate.items() == null || candidate.items().isEmpty())) {
            status = "UNKNOWN";
        }
        items.add(EvidenceItem.builder()
                .evidenceId(id)
                .sourceType("TOOL")
                .sourceId(toolName)
                .sourceVersion("v1")
                .asOf(result.getAsOf() == null ? LocalDateTime.now() : result.getAsOf())
                .status(status)
                .summary(summary)
                .errorCode(result.getErrorCode())
                .build());
        return result.getData();
    }

    private String limit(String value) {
        if (value == null) {
            return "";
        }
        return value.length() > 12000 ? value.substring(0, 12000) : value;
    }
}
// AI_GENERATE_END --
