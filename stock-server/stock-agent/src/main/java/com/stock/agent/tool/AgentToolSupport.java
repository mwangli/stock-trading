// AI_GENERATE_START--------------
package com.stock.agent.tool;

import com.stock.agent.model.ToolExecutionContext;
import com.stock.agent.service.AgentToolAuditService;
import com.stock.agent.config.AgentProperties;
import com.stock.agent.support.FactSourceUnavailableException;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Agent 工具执行公共支持。
 * 只负责超时和脱敏审计，不负责工具发现、名称分发或权限绕过。
 *
 * @author mwangli
 * @since 2026-10-18
 */
@Component
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
        return execute(toolName, action, result -> List.of());
    }

    /**
     * 在统一超时和审计边界内执行只读工具，并记录工具实际返回的来源引用。
     *
     * @param toolName 工具名称
     * @param action 工具业务逻辑
     * @param evidenceRefExtractor 成功结果来源提取器
     * @param <T> 返回类型
     * @return 工具结果
     */
    public <T> T execute(String toolName, Supplier<T> action,
                         Function<T, List<String>> evidenceRefExtractor) {
        ToolExecutionContext context = AgentToolContextHolder.current();
        LocalDateTime startedAt = LocalDateTime.now();
        if (context == null || !StringUtils.hasText(context.getUserScope())) {
            auditService.record(toolName, context, "FAILED", "SCOPE_REQUIRED", List.of(), startedAt);
            throw new IllegalArgumentException("工具调用缺少用户范围: " + toolName);
        }
        if (context.getAllowedToolNames() == null || !context.getAllowedToolNames().contains(toolName)) {
            auditService.record(toolName, context, "FAILED", "TOOL_NOT_ALLOWED", List.of(), startedAt);
            throw new IllegalArgumentException("工具不在本轮允许范围: " + toolName);
        }
        Future<T> future = executor.submit(action::get);
        try {
            T result = future.get(
                    Math.max(properties.getToolTimeoutMs(), 1L), TimeUnit.MILLISECONDS);
            List<String> evidenceRefs = evidenceRefExtractor == null ? List.of()
                    : evidenceRefExtractor.apply(result);
            auditService.record(toolName, context, "SUCCESS", null, evidenceRefs, startedAt);
            return result;
        } catch (TimeoutException exception) {
            future.cancel(true);
            auditService.record(toolName, context, "FAILED", "TOOL_TIMEOUT", List.of(), startedAt);
            throw new IllegalStateException("工具执行超时: " + toolName, exception);
        } catch (InterruptedException exception) {
            future.cancel(true);
            Thread.currentThread().interrupt();
            auditService.record(toolName, context, "FAILED", "TOOL_INTERRUPTED", List.of(), startedAt);
            throw new IllegalStateException("工具执行被中断: " + toolName, exception);
        } catch (ExecutionException exception) {
            future.cancel(true);
            Throwable cause = exception.getCause();
            if (cause instanceof FactSourceUnavailableException factSourceException) {
                auditService.record(toolName, context, "FAILED",
                        FactSourceUnavailableException.ERROR_CODE, List.of(), startedAt);
                throw factSourceException;
            }
            auditService.record(toolName, context, "FAILED", "TOOL_EXECUTION_FAILED", List.of(), startedAt);
            throw new IllegalStateException("工具执行失败: " + toolName, cause);
        } catch (RuntimeException exception) {
            future.cancel(true);
            auditService.record(toolName, context, "FAILED", "TOOL_EXECUTION_FAILED", List.of(), startedAt);
            throw exception;
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
// AI_GENERATE_END-------------
