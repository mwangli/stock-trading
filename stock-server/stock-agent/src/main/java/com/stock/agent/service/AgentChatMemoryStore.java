// AI_GENERATE_START-----------
package com.stock.agent.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.stock.agent.config.AgentProperties;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Duration;

/**
 * LangChain4j 短期会话记忆提供服务。
 * 按 Session ID 隔离上下文，不把长期 Memory 和短期 ChatMemory 混用。
 *
 * @author mwangli
 * @since 2026-10-17
 */
@Component
public class AgentChatMemoryStore {

    private final AgentProperties properties;
    private final Cache<String, ChatMemory> memories;
    private final Cache<String, PendingAttempt> pendingAttempts;

    /**
     * 创建有容量和空闲过期边界的进程内短期记忆存储。
     *
     * @param properties Agent 运行配置
     */
    public AgentChatMemoryStore(AgentProperties properties) {
        this.properties = properties;
        long maximumSessions = Math.max(properties.getChatMemoryMaxSessions(), 1);
        Duration idleTtl = Duration.ofMinutes(Math.max(properties.getChatMemoryIdleTtlMinutes(), 1));
        this.memories = Caffeine.newBuilder()
                .maximumSize(maximumSessions)
                .expireAfterAccess(idleTtl)
                .build();
        this.pendingAttempts = Caffeine.newBuilder()
                .maximumSize(maximumSessions)
                .expireAfterWrite(idleTtl)
                .build();
    }

    /**
     * 创建或获取指定会话的短期记忆。
     *
     * @param sessionId 会话标识
     * @return 会话记忆
     */
    public ChatMemory get(String sessionId) {
        String key = StringUtils.hasText(sessionId) ? sessionId : "anonymous";
        return memories.get(key, ignored -> MessageWindowChatMemory.builder()
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
        PendingAttempt pendingAttempt = pendingAttempts.asMap().remove(taskId);
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
            pendingAttempts.invalidate(taskId);
        }
    }

    /**
     * 清理指定会话的短期记忆。
     *
     * @param sessionId 会话标识
     */
    public void clear(String sessionId) {
        if (StringUtils.hasText(sessionId)) {
            memories.invalidate(sessionId);
        }
    }

    private record PendingAttempt(String sessionId, ChatMemory memory) {
    }
}
// AI_GENERATE_END----------
