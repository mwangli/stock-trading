// AI_GENERATE_START ----
package com.stock.strategyAgent.runtime;

import com.stock.strategyAgent.config.AgentProperties;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 基于 LangChain4j ChatModel 的 Agent 模型适配器。
 *
 * @author mwangli
 * @since 2026-10-17
 */
@Slf4j
@Primary
@Service
@RequiredArgsConstructor
public class LangChain4jAgentModelProvider implements AgentModelProvider {

    private final ObjectProvider<ChatModel> chatModelProvider;
    private final AgentProperties properties;
    private final AgentChatMemoryService chatMemoryService;

    /**
     * 使用 LangChain4j 执行结构化 Runtime 请求。
     *
     * @param request Runtime 请求
     * @return Runtime 响应
     */
    @Override
    public AgentRuntimeResponse generate(AgentRuntimeRequest request) {
        if (request == null) {
            return AgentRuntimeResponse.builder()
                    .status(AgentRuntimeStatus.FAILED)
                    .provider(providerName())
                    .errorCode("INVALID_RUNTIME_REQUEST")
                    .content("Runtime 请求不能为空")
                    .build();
        }
        if (!properties.isEnabled() || !StringUtils.hasText(properties.getApiKey())
                || !StringUtils.hasText(properties.getApiUrl())
                || !StringUtils.hasText(properties.getModel())) {
            return degraded(request, "AGENT_MODEL_NOT_CONFIGURED", "Agent 未配置可用的 LangChain4j 外部模型");
        }
        ChatModel model = chatModelProvider.getIfAvailable();
        if (model == null) {
            return degraded(request, "CHAT_MODEL_UNAVAILABLE", "LangChain4j ChatModel 未创建");
        }
        try {
            ChatMemory memory = chatMemoryService.get(request.getSessionId());
            memory.add(SystemMessage.from(request.getSystemPrompt()));
            memory.add(UserMessage.from(request.getUserPrompt()));
            String content = model.chat(memory.messages()).aiMessage().text();
            if (!StringUtils.hasText(content)) {
                return degraded(request, "MODEL_EMPTY_RESPONSE", "LangChain4j 模型未返回文本内容");
            }
            memory.add(dev.langchain4j.data.message.AiMessage.from(content));
            return AgentRuntimeResponse.builder()
                    .status(AgentRuntimeStatus.COMPLETED)
                    .provider(providerName())
                    .model(properties.getModel())
                    .content(limit(content))
                    .evidenceSummary(request.getEvidenceSummary())
                    .build();
        } catch (RuntimeException exception) {
            log.warn("[Agent] LangChain4j 模型调用失败", exception);
            return degraded(request, "MODEL_CALL_FAILED", "LangChain4j 模型调用失败");
        }
    }

    /**
     * 返回提供方标识。
     *
     * @return 提供方名称
     */
    @Override
    public String providerName() {
        return "langchain4j:" + properties.getProvider();
    }

    private AgentRuntimeResponse degraded(AgentRuntimeRequest request, String errorCode, String reason) {
        return AgentRuntimeResponse.builder()
                .status(AgentRuntimeStatus.DEGRADED)
                .provider(providerName())
                .model(properties.getModel())
                .content(reason + "，已保留结构化事实摘要。")
                .evidenceSummary(request.getEvidenceSummary())
                .errorCode(errorCode)
                .build();
    }

    private String limit(String value) {
        if (value == null) {
            return "";
        }
        return value.length() > properties.getMaxOutputChars()
                ? value.substring(0, properties.getMaxOutputChars()) : value;
    }
}
// AI_GENERATE_END ----
