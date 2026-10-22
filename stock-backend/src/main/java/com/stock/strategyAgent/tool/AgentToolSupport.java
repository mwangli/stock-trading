// AI_GENERATE_START -----
package com.stock.strategyAgent.tool;

import com.stock.strategyAgent.audit.AgentToolAuditService;
import com.stock.strategyAgent.config.AgentProperties;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

/**
 * Agent 工具执行公共支持。
 * 只负责超时和脱敏审计，不负责工具发现、名称分发或权限绕过。
 *
 * @author mwangli
 * @since 2026-10-18
 */
@Service
@RequiredArgsConstructor
public class AgentToolSupport {

    private final AgentProperties properties;
    private final AgentToolAuditService auditService;
    private final ExecutorService executor = Executors.newCachedThreadPool();

    /**
     * 在统一超时和审计边界内执行只读工具。
     *
     * @param toolName 工具名称
     * @param action 工具业务逻辑
     * @param <T> 返回类型
     * @return 工具结果
     */
    public <T> T execute(String toolName, Supplier<T> action) {
        ToolExecutionContext context = AgentToolContextHolder.current();
        LocalDateTime startedAt = LocalDateTime.now();
        if (context == null || !StringUtils.hasText(context.getUserScope())) {
            auditService.record(toolName, context, "FAILED", "SCOPE_REQUIRED", startedAt);
            throw new IllegalArgumentException("工具调用缺少用户范围: " + toolName);
        }
        if (context.getAllowedToolNames() == null || !context.getAllowedToolNames().contains(toolName)) {
            auditService.record(toolName, context, "FAILED", "TOOL_NOT_ALLOWED", startedAt);
            throw new IllegalArgumentException("工具不在本轮允许范围: " + toolName);
        }
        Future<T> future = executor.submit(action::get);
        try {
            T result = future.get(
                    Math.max(properties.getToolTimeoutMs(), 1L), TimeUnit.MILLISECONDS);
            auditService.record(toolName, context, "SUCCESS", null, startedAt);
            return result;
        } catch (TimeoutException exception) {
            future.cancel(true);
            auditService.record(toolName, context, "FAILED", "TOOL_TIMEOUT", startedAt);
            throw new IllegalStateException("工具执行超时: " + toolName, exception);
        } catch (InterruptedException exception) {
            future.cancel(true);
            Thread.currentThread().interrupt();
            auditService.record(toolName, context, "FAILED", "TOOL_INTERRUPTED", startedAt);
            throw new IllegalStateException("工具执行被中断: " + toolName, exception);
        } catch (Exception exception) {
            future.cancel(true);
            auditService.record(toolName, context, "FAILED", "TOOL_EXECUTION_FAILED", startedAt);
            throw new IllegalStateException("工具执行失败: " + toolName, exception.getCause());
        }
    }

    /**
     * 关闭工具执行线程池。
     */
    @PreDestroy
    public void shutdown() {
        executor.shutdownNow();
    }
}
// AI_GENERATE_END -----
