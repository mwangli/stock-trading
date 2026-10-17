// AI_GENERATE_START ---
package com.stock.strategyAgent.runtime;

import com.alibaba.fastjson2.JSON;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Set;

/**
 * Agent 结构化输出解析器。
 *
 * @author mwangli
 * @since 2026-10-17
 */
@Component
public class AgentStructuredOutputParser {

    private static final Set<String> RESEARCH_STATUS = Set.of("PASS", "WARN", "UNKNOWN");
    private static final Set<String> DIAGNOSTIC_STATUS = Set.of("PASS", "WARN", "BLOCKED", "UNKNOWN");
    private static final Set<String> REPORT_STATUS = Set.of("PASS", "WARN", "UNKNOWN");
    private static final Set<String> TRADE_PLAN_STATUS = Set.of("PENDING_REVALIDATION");
    private static final Set<String> REPORT_SECTIONS = Set.of(
            "marketOverview", "candidateSummary", "modelSignals", "sentimentSignals",
            "strategyStatus", "riskWarnings", "dataQuality", "abnormalJobs");

    /**
     * 将模型 JSON 解析为当前任务对应的结构化结果。
     *
     * @param outputType 输出类型
     * @param content 模型原始内容
     * @param allowedEvidenceRefs 本次任务允许引用的证据编号
     * @return 结构化结果
     */
    public AgentStructuredOutput parse(String outputType, String content, List<String> allowedEvidenceRefs) {
        if (!StringUtils.hasText(outputType) || !StringUtils.hasText(content)) {
            throw new IllegalArgumentException("结构化输出类型或内容不能为空");
        }
        try {
            AgentStructuredOutput output = switch (outputType) {
                case "RESEARCH" -> JSON.parseObject(content, ResearchResult.class);
                case "DIAGNOSTIC" -> JSON.parseObject(content, DiagnosticResult.class);
                case "REPORT" -> JSON.parseObject(content, ReportResult.class);
                case "TRADE_PLAN" -> JSON.parseObject(content, TradePlanDraft.class);
                default -> throw new IllegalArgumentException("不支持的结构化输出类型: " + outputType);
            };
            validate(outputType, output);
            validateEvidenceRefs(output, allowedEvidenceRefs);
            return output;
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("模型未返回合法的结构化 JSON", exception);
        }
    }

    private void validateEvidenceRefs(AgentStructuredOutput output, List<String> allowedEvidenceRefs) {
        Set<String> allowed = allowedEvidenceRefs == null ? Set.of() : Set.copyOf(allowedEvidenceRefs);
        if (output.getEvidenceRefs() == null || output.getEvidenceRefs().stream()
                .anyMatch(ref -> !StringUtils.hasText(ref) || !allowed.contains(ref))) {
            throw new IllegalArgumentException("结构化输出包含未授权的证据引用");
        }
    }

    private void validate(String outputType, AgentStructuredOutput output) {
        if (output == null || !StringUtils.hasText(output.getSummary())
                || !StringUtils.hasText(output.getStatus())) {
            throw new IllegalArgumentException("结构化输出缺少 summary 或 status");
        }
        if (output.getEvidenceRefs() == null) {
            if (output instanceof ResearchResult result) {
                result.setEvidenceRefs(List.of());
            } else if (output instanceof DiagnosticResult result) {
                result.setEvidenceRefs(List.of());
            } else if (output instanceof ReportResult result) {
                result.setEvidenceRefs(List.of());
            }
        }
        Set<String> allowed = switch (outputType) {
            case "RESEARCH" -> RESEARCH_STATUS;
            case "DIAGNOSTIC" -> DIAGNOSTIC_STATUS;
            case "REPORT" -> REPORT_STATUS;
            case "TRADE_PLAN" -> TRADE_PLAN_STATUS;
            default -> Set.of();
        };
        if (!allowed.contains(output.getStatus())) {
            throw new IllegalArgumentException("结构化输出状态非法: " + output.getStatus());
        }
        if (output instanceof DiagnosticResult result) {
            validateChecks(result);
        }
        if (output instanceof ReportResult result) {
            validateReport(result);
        }
        if (output instanceof TradePlanDraft draft) {
            validateTradePlan(draft);
        }
    }

    private void validateChecks(DiagnosticResult result) {
        if (result.getChecks() == null || result.getChecks().isEmpty()) {
            throw new IllegalArgumentException("诊断结构化输出缺少 checks");
        }
        for (DiagnosticResult.DiagnosticCheck check : result.getChecks()) {
            if (check == null || !StringUtils.hasText(check.getName())
                    || !DIAGNOSTIC_STATUS.contains(check.getStatus())
                    || !StringUtils.hasText(check.getDetail())) {
                throw new IllegalArgumentException("诊断检查项结构非法");
            }
            if (check.getEvidenceRefs() == null) {
                check.setEvidenceRefs(List.of());
            }
        }
    }

    private void validateReport(ReportResult result) {
        if (!StringUtils.hasText(result.getReportDate()) || result.getSections() == null
                || !result.getSections().keySet().containsAll(REPORT_SECTIONS)) {
            throw new IllegalArgumentException("报告结构化输出缺少固定章节");
        }
    }

    private void validateTradePlan(TradePlanDraft draft) {
        if (!"PENDING_REVALIDATION".equals(draft.getStatus())
                || !StringUtils.hasText(draft.getCandidateBatchId())
                || !StringUtils.hasText(draft.getPredictionBatchId())
                || !StringUtils.hasText(draft.getStockCode())
                || draft.getTargetTradeDate() == null
                || !StringUtils.hasText(draft.getStrategyVersion())
                || (!"BUY".equalsIgnoreCase(draft.getDirection()) && !"SELL".equalsIgnoreCase(draft.getDirection()))
                || draft.getExpiresAt() == null || !draft.getExpiresAt().isAfter(java.time.LocalDateTime.now())
                || draft.getEvidenceRefs() == null || draft.getEvidenceRefs().isEmpty()
                || !StringUtils.hasText(draft.getIdempotencyKey())) {
            throw new IllegalArgumentException("交易计划草案字段或状态非法");
        }
        if (draft.getPlannedAmount() == null || draft.getPlannedAmount().signum() <= 0) {
            throw new IllegalArgumentException("交易计划必须包含正数金额");
        }
        if ("SELL".equalsIgnoreCase(draft.getDirection())
                && (draft.getPlannedQuantity() == null || draft.getPlannedQuantity() <= 0
                || draft.getPlannedQuantity() % 100 != 0)) {
            throw new IllegalArgumentException("卖出交易计划必须包含有效数量");
        }
    }
}
// AI_GENERATE_END ---
