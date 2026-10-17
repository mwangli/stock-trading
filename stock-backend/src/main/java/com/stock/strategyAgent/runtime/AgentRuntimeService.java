// AI_GENERATE_START -----
package com.stock.strategyAgent.runtime;

import com.stock.strategyAgent.config.AgentProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import java.util.Set;

/**
 * Agent Runtime 统一编排服务。
 *
 * @author mwangli
 * @since 2026-10-17
 */
@Service
@RequiredArgsConstructor
public class AgentRuntimeService {

    private static final Set<String> SUPPORTED_OUTPUT_TYPES = Set.of("RESEARCH", "DIAGNOSTIC", "REPORT", "TRADE_PLAN");

    private final AgentModelProvider modelProvider;
    private final AgentProperties properties;
    private final AgentStructuredOutputParser structuredOutputParser;

    /**
     * 执行一次受控模型调用，并统一处理输入、输出和降级状态。
     *
     * @param request Runtime 请求
     * @return Runtime 响应
     */
    public AgentRuntimeResponse execute(AgentRuntimeRequest request) {
        if (request == null || !SUPPORTED_OUTPUT_TYPES.contains(request.getOutputType())) {
            return failure(request, "INVALID_OUTPUT_TYPE", "Runtime 输出类型不受支持");
        }
        if (request == null || !StringUtils.hasText(request.getUserPrompt())
                || !StringUtils.hasText(request.getSystemPrompt())) {
            return failure(request, "INVALID_RUNTIME_REQUEST", "Runtime 请求缺少必要提示词");
        }
        if (request.getUserPrompt().length() > 20000 || request.getSystemPrompt().length() > 12000) {
            return failure(request, "RUNTIME_INPUT_TOO_LARGE", "Runtime 输入超过长度限制");
        }
        if (!properties.isEnabled()) {
            return degraded(request, "AGENT_DISABLED", "Agent 未启用，返回结构化事实摘要");
        }
        AgentRuntimeResponse response = modelProvider.generate(request);
        if (response == null || response.getStatus() == null) {
            return failure(request, "RUNTIME_EMPTY_RESPONSE", "Runtime 未返回有效状态");
        }
        if (response.getStatus() == AgentRuntimeStatus.COMPLETED) {
            try {
                AgentStructuredOutput structuredOutput = structuredOutputParser.parse(
                        request.getOutputType(), response.getContent(), request.getEvidenceRefs());
                response = AgentRuntimeResponse.builder()
                        .status(response.getStatus())
                        .provider(response.getProvider())
                        .model(response.getModel())
                        .content(response.getContent())
                        .evidenceSummary(response.getEvidenceSummary())
                        .errorCode(response.getErrorCode())
                        .structuredOutput(structuredOutput)
                        .build();
            } catch (IllegalArgumentException exception) {
                return failure(request, "STRUCTURED_OUTPUT_INVALID", exception.getMessage());
            }
        }
        return limit(response);
    }

    /**
     * 返回当前 Runtime 使用的模型提供方。
     *
     * @return 提供方名称
     */
    public String providerName() {
        return modelProvider.providerName();
    }

    /**
     * 查询模型 Provider 的脱敏状态。
     *
     * @return Provider 状态
     */
    public AgentModelProviderStatus providerStatus() {
        return modelProvider.status();
    }

    private AgentRuntimeResponse limit(AgentRuntimeResponse response) {
        String content = response.getContent();
        if (content != null && content.length() > properties.getMaxOutputChars()) {
            content = content.substring(0, properties.getMaxOutputChars());
        }
        return AgentRuntimeResponse.builder()
                .status(response.getStatus())
                .provider(response.getProvider())
                .model(response.getModel())
                .content(content)
                .evidenceSummary(response.getEvidenceSummary())
                .errorCode(response.getErrorCode())
                .structuredOutput(response.getStructuredOutput())
                .build();
    }

    private AgentRuntimeResponse degraded(AgentRuntimeRequest request, String errorCode, String message) {
        return AgentRuntimeResponse.builder()
                .status(AgentRuntimeStatus.DEGRADED)
                .provider(modelProvider.providerName())
                .content(message)
                .evidenceSummary(request == null ? null : request.getEvidenceSummary())
                .errorCode(errorCode)
                .build();
    }

    private AgentRuntimeResponse failure(AgentRuntimeRequest request, String errorCode, String message) {
        return AgentRuntimeResponse.builder()
                .status(AgentRuntimeStatus.FAILED)
                .provider(modelProvider.providerName())
                .content(message)
                .evidenceSummary(request == null ? null : request.getEvidenceSummary())
                .errorCode(errorCode)
                .build();
    }
}
// AI_GENERATE_END -----
