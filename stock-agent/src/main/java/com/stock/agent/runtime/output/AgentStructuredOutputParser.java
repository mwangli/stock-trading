// AI_GENERATE_START --
package com.stock.agent.runtime.output;

import com.stock.agent.runtime.api.AgentStructuredOutput;
import com.stock.agent.runtime.api.AgentStructuredOutputHandler;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Set;

/**
 * Agent 结构化输出统一入口。
 *
 * Runtime 只处理公共证据约束，具体业务字段和状态由所属能力的 Handler 校验。
 *
 * @author mwangli
 * @since 2026-10-17
 */
@Component
public class AgentStructuredOutputParser {

    private final List<AgentStructuredOutputHandler> handlers;

    /**
     * 创建结构化输出解析器。
     *
     * @param handlers 各业务能力注册的输出处理器
     */
    public AgentStructuredOutputParser(List<AgentStructuredOutputHandler> handlers) {
        this.handlers = List.copyOf(handlers);
    }

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
        AgentStructuredOutputHandler handler = handlers.stream()
                .filter(item -> item.supports(outputType))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("不支持的结构化输出类型: " + outputType));
        try {
            AgentStructuredOutput output = handler.parseAndValidate(outputType, normalizeJsonContent(content));
            validateEvidenceRefs(output, allowedEvidenceRefs);
            return output;
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("模型未返回合法的结构化 JSON", exception);
        }
    }

    private String normalizeJsonContent(String content) {
        String normalized = content.trim();
        if (normalized.startsWith("```")) {
            int firstLineEnd = normalized.indexOf('\n');
            int closingFence = normalized.lastIndexOf("```");
            if (firstLineEnd > 0 && closingFence > firstLineEnd) {
                normalized = normalized.substring(firstLineEnd + 1, closingFence).trim();
            }
        }
        int objectStart = normalized.indexOf('{');
        int objectEnd = normalized.lastIndexOf('}');
        return objectStart >= 0 && objectEnd > objectStart
                ? normalized.substring(objectStart, objectEnd + 1) : normalized;
    }

    private void validateEvidenceRefs(AgentStructuredOutput output, List<String> allowedEvidenceRefs) {
        Set<String> allowed = allowedEvidenceRefs == null ? Set.of() : Set.copyOf(allowedEvidenceRefs);
        if (output == null || output.getEvidenceRefs() == null || output.getEvidenceRefs().stream()
                .anyMatch(ref -> !StringUtils.hasText(ref) || !allowed.contains(ref))) {
            throw new IllegalArgumentException("结构化输出包含未授权的证据引用");
        }
    }
}
// AI_GENERATE_END -
