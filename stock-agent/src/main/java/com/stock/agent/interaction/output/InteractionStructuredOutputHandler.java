// AI_GENERATE_START ------
package com.stock.agent.interaction.output;

import com.alibaba.fastjson2.JSON;
import com.stock.agent.runtime.api.AgentStructuredOutput;
import com.stock.agent.runtime.api.AgentStructuredOutputHandler;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Set;

/**
 * 研究、诊断和报告结构化输出处理器。
 *
 * @author mwangli
 * @since 2026-10-24
 */
@Component
public class InteractionStructuredOutputHandler implements AgentStructuredOutputHandler {

    private static final Set<String> TYPES = Set.of("CONVERSATION", "RESEARCH", "DIAGNOSTIC", "REPORT");
    private static final Set<String> RESEARCH_STATUS = Set.of("PASS", "WARN", "UNKNOWN");
    private static final Set<String> DIAGNOSTIC_STATUS = Set.of("PASS", "WARN", "BLOCKED", "UNKNOWN");
    private static final Set<String> REPORT_STATUS = Set.of("PASS", "WARN", "UNKNOWN");
    private static final Set<String> REPORT_SECTIONS = Set.of(
            "marketOverview", "candidateSummary", "modelSignals", "sentimentSignals",
            "strategyStatus", "riskWarnings", "dataQuality", "abnormalJobs");

    /** {@inheritDoc} */
    @Override
    public boolean supports(String outputType) {
        return TYPES.contains(outputType);
    }

    /** {@inheritDoc} */
    @Override
    public AgentStructuredOutput parseAndValidate(String outputType, String content) {
        AgentStructuredOutput output = switch (outputType) {
            case "CONVERSATION" -> parseConversation(content);
            case "RESEARCH" -> JSON.parseObject(content, ResearchResult.class);
            case "DIAGNOSTIC" -> JSON.parseObject(content, DiagnosticResult.class);
            case "REPORT" -> JSON.parseObject(content, ReportResult.class);
            default -> throw new IllegalArgumentException("不支持的交互输出类型: " + outputType);
        };
        validateCommon(outputType, output);
        if (output instanceof DiagnosticResult result) validateChecks(result);
        if (output instanceof ReportResult result) validateReport(result);
        return output;
    }

    private ConversationResult parseConversation(String content) {
        ConversationResult result = new ConversationResult();
        result.setSummary(content.trim());
        result.setStatus("PASS");
        result.setEvidenceRefs(List.of());
        return result;
    }

    private void validateCommon(String outputType, AgentStructuredOutput output) {
        if (output == null || !StringUtils.hasText(output.getSummary()) || !StringUtils.hasText(output.getStatus())) {
            throw new IllegalArgumentException("结构化输出缺少 summary 或 status");
        }
        if (output instanceof ResearchResult result) {
            if ("NO_ACTION".equalsIgnoreCase(result.getStatus())) result.setStatus("UNKNOWN");
            if ("PARTIAL_EVIDENCE".equalsIgnoreCase(result.getStatus())) result.setStatus("WARN");
            if (result.getEvidenceRefs() == null) result.setEvidenceRefs(List.of());
        } else if (output instanceof DiagnosticResult result && result.getEvidenceRefs() == null) {
            result.setEvidenceRefs(List.of());
        } else if (output instanceof ReportResult result && result.getEvidenceRefs() == null) {
            result.setEvidenceRefs(List.of());
        }
        Set<String> allowed = switch (outputType) {
            case "CONVERSATION" -> RESEARCH_STATUS;
            case "RESEARCH" -> RESEARCH_STATUS;
            case "DIAGNOSTIC" -> DIAGNOSTIC_STATUS;
            case "REPORT" -> REPORT_STATUS;
            default -> Set.of();
        };
        if (!allowed.contains(output.getStatus())) {
            throw new IllegalArgumentException("结构化输出状态非法: " + output.getStatus());
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
            if (check.getEvidenceRefs() == null) check.setEvidenceRefs(List.of());
        }
    }

    private void validateReport(ReportResult result) {
        if (!StringUtils.hasText(result.getReportDate()) || result.getSections() == null
                || !result.getSections().keySet().containsAll(REPORT_SECTIONS)) {
            throw new IllegalArgumentException("报告结构化输出缺少固定章节");
        }
    }
}
// AI_GENERATE_END -----
