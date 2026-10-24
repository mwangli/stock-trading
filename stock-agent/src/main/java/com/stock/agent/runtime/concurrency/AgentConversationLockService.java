// AI_GENERATE_START-----
package com.stock.agent.runtime.concurrency;

import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

/**
 * Agent 会话单轮串行执行服务。
 * 首期在单应用实例内保证同一会话消息顺序，后续多实例部署应替换为数据库轮次或分布式锁。
 *
 * @author mwangli
 * @since 2026-10-22
 */
@Service
public class AgentConversationLockService {

    private final ConcurrentHashMap<String, LockEntry> locks = new ConcurrentHashMap<>();

    /**
     * 在指定会话键的互斥边界内执行一轮对话。
     *
     * @param conversationKey 会话标识；新会话使用认证用户范围
     * @param action 对话执行逻辑
     * @param <T> 返回类型
     * @return 对话执行结果
     */
    public <T> T execute(String conversationKey, Supplier<T> action) {
        LockEntry entry = locks.compute(conversationKey, (ignored, current) -> {
            LockEntry target = current == null ? new LockEntry() : current;
            target.references.incrementAndGet();
            return target;
        });
        entry.lock.lock();
        try {
            return action.get();
        } finally {
            entry.lock.unlock();
            locks.computeIfPresent(conversationKey, (ignored, current) ->
                    current == entry && entry.references.decrementAndGet() == 0 ? null : current);
        }
    }

    private static final class LockEntry {
        private final ReentrantLock lock = new ReentrantLock();
        private final AtomicInteger references = new AtomicInteger();
    }
}
// AI_GENERATE_END----
