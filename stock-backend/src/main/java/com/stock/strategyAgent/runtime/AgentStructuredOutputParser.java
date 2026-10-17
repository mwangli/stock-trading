// AI_GENERATE_START --
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
    private static final Set<String> REPORT_SECTIONS = Set.of(
            "marketOverview", "candidateSummary", "modelSignals", "sentimentSignals",
            "strategyStatus", "riskWarnings", "dataQuality", "abnormalJobs");

    /**
     * 将模型 JSON 解析为当前任务对应的结构化结果。
     *
     * @param outputType 输出类型
     * @param content 模型原始内容
     * @return 结构化结果
     */
    public AgentStructuredOutput parse(String outputType, String content) {
        if (!StringUtils.hasText(outputType) || !StringUtils.hasText(content)) {
            throw new IllegalArgumentException("结构化输出类型或内容不能为空");
        }
        try {
            AgentStructuredOutput output = switch (outputType) {
                case "RESEARCH" -> JSON.parseObject(content, ResearchResult.class);
                case "DIAGNOSTIC" -> JSON.parseObject(content, DiagnosticResult.class);
                case "REPORT" -> JSON.parseObject(content, ReportResult.class);
                default -> throw new IllegalArgumentException("不支持的结构化输出类型: " + outputType);
            };
            validate(outputType, output);
            return output;
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("模型未返回合法的结构化 JSON", exception);
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
}
// AI_GENERATE_END --