// AI_GENERATE_START -
package com.stock.strategyAgent.runtime;

import com.stock.strategyAgent.config.AgentProperties;
import dev.langchain4j.memory.ChatMemory;
import lombok.RequiredArgsConstructor;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * LangChain4j 短期会话记忆提供服务。
 * 按 Session ID 隔离上下文，不把长期 Memory 和短期 ChatMemory 混用。
 *
 * @author mwangli
 * @since 2026-10-17
 */
@Service
@RequiredArgsConstructor
public class AgentChatMemoryService {

    private final Map<String, ChatMemory> memories = new ConcurrentHashMap<>();
    private final AgentProperties properties;

    /**
     * 创建或获取指定会话的短期记忆。
     *
     * @param sessionId 会话标识
     * @return 会话记忆
     */
    public ChatMemory get(String sessionId) {
        String key = StringUtils.hasText(sessionId) ? sessionId : "anonymous";
        return memories.computeIfAbsent(key, ignored -> MessageWindowChatMemory.builder()
                .id(key)
                .maxMessages(Math.max(properties.getChatMemoryMaxMessages(), 2))
                .build());
    }

    /**
     * 清理指定会话的短期记忆。
     *
     * @param sessionId 会话标识
     */
    public void clear(String sessionId) {
        if (StringUtils.hasText(sessionId)) {
            memories.remove(sessionId);
        }
    }
}
// AI_GENERATE_END -
