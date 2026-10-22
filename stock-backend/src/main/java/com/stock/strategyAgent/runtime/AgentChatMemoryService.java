// AI_GENERATE_START ---
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
    private final Map<String, PendingAttempt> pendingAttempts = new ConcurrentHashMap<>();
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
     * 创建当前会话 Memory 的独立 Provider 尝试快照。
     * 失败、超时或取消的 Provider 尝试只修改快照，不污染正式会话 Memory。
     *
     * @param sessionId 会话标识
     * @return 独立尝试 Memory
     */
    public ChatMemory snapshot(String sessionId) {
        ChatMemory source = get(sessionId);
        ChatMemory snapshot = MessageWindowChatMemory.builder()
                .id(sessionId + ":attempt")
                .maxMessages(Math.max(properties.getChatMemoryMaxMessages(), 2))
                .build();
        synchronized (source) {
            source.messages().forEach(snapshot::add);
        }
        return snapshot;
    }

    /**
     * 暂存已完成模型调用、尚未通过 Runtime 输出校验的 Memory。
     *
     * @param taskId 任务标识
     * @param sessionId 会话标识
     * @param successfulAttempt 已完成模型调用的尝试 Memory
     */
    public void stage(String taskId, String sessionId, ChatMemory successfulAttempt) {
        pendingAttempts.put(taskId, new PendingAttempt(sessionId, successfulAttempt));
    }

    /**
     * 将已通过结构化输出校验的 Provider 尝试原子提交为正式会话 Memory。
     *
     * @param taskId 任务标识
     */
    public void commit(String taskId) {
        PendingAttempt pendingAttempt = pendingAttempts.remove(taskId);
        if (pendingAttempt == null) {
            return;
        }
        String sessionId = pendingAttempt.sessionId();
        ChatMemory successfulAttempt = pendingAttempt.memory();
        ChatMemory target = get(sessionId);
        synchronized (target) {
            target.clear();
            successfulAttempt.messages().forEach(target::add);
        }
    }

    /**
     * 丢弃未通过完整 Runtime 校验的 Provider 尝试。
     *
     * @param taskId 任务标识
     */
    public void discard(String taskId) {
        if (taskId != null) {
            pendingAttempts.remove(taskId);
        }
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

    private record PendingAttempt(String sessionId, ChatMemory memory) {
    }
}
// AI_GENERATE_END ---
