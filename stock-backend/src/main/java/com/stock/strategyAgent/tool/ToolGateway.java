// AI_GENERATE_START ---
package com.stock.strategyAgent.tool;

import com.stock.strategyAgent.audit.AgentToolAuditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * LangChain4j 工具调用安全网关。
 * 模型只能调用显式注册的工具，不能把工具名、SQL、Mongo 查询或方法名交给反射执行。
 *
 * @author mwangli
 * @since 2026-10-17
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ToolGateway {

    private final Map<String, RegisteredTool> tools = new ConcurrentHashMap<>();
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final AgentToolAuditService auditService;

    /**
     * 注册只读工具。
     *
     * @param definition 工具定义
     * @param executor 工具执行函数
     */
    public void register(ToolDefinition definition, ToolExecutor<?> executor) {
        if (definition == null || !StringUtils.hasText(definition.getName()) || executor == null) {
            throw new IllegalArgumentException("工具定义和执行函数不能为空");
        }
        if (!definition.isReadOnly()) {
            throw new IllegalArgumentException("二期 Tool Gateway 只允许注册只读工具");
        }
        if (definition.getRequiredPermission() == null) {
            throw new IllegalArgumentException("工具必须声明权限");
        }
        tools.put(definition.getName(), new RegisteredTool(definition, executor));
    }

    /**
     * 执行已注册工具。
     *
     * @param name 工具名称
     * @param input 已反序列化的 DTO
     * @param context 调用上下文
     * @return 统一执行结果
     */
    public ToolExecutionResult<Object> execute(String name, Object input, ToolExecutionContext context) {
        java.time.LocalDateTime startedAt = java.time.LocalDateTime.now();
        RegisteredTool tool = tools.get(name);
        if (tool == null) {
            return failure(name, "TOOL_NOT_ALLOWED", "工具未注册或不允许调用");
        }
        if (context == null || !StringUtils.hasText(context.getUserScope())) {
            return failure(name, "SCOPE_REQUIRED", "工具调用缺少用户范围");
        }
        if (tool.definition.getRequiredPermission() == ToolPermission.USER_SCOPED_READ
                && !StringUtils.hasText(context.getUserScope())) {
            return failure(name, "USER_SCOPE_REQUIRED", "用户范围读取工具缺少用户范围");
        }
        Future<Object> future = executor.submit(() -> tool.executor.execute(input, context));
        try {
            Object data = future.get(Math.max(tool.definition.getTimeoutMs(), 1L), TimeUnit.MILLISECONDS);
            String summary = data == null ? null : limit(String.valueOf(data), tool.definition.getMaxResultSize());
            log.info("[AgentTool] 工具执行成功: name={}, version={}, traceId={}, resultSize={}",
                    name, tool.definition.getVersion(), context.getTraceId(), summary == null ? 0 : summary.length());
            ToolExecutionResult<Object> result = ToolExecutionResult.builder()
                    .success(true)
                    .status("SUCCESS")
                    .source(name)
                    .asOf(LocalDateTime.now())
                    .data(data)
                    .build();
            auditService.record(tool.definition, context, result, startedAt);
            return result;
        } catch (java.util.concurrent.TimeoutException exception) {
            future.cancel(true);
            log.warn("[AgentTool] 工具执行超时: name={}, traceId={}, timeoutMs={}",
                    name, context.getTraceId(), tool.definition.getTimeoutMs());
            ToolExecutionResult<Object> result = failure(name, "TOOL_TIMEOUT", "工具执行超时");
            auditService.record(tool.definition, context, result, startedAt);
            return result;
        } catch (Exception exception) {
            future.cancel(true);
            log.warn("[AgentTool] 工具执行失败: name={}, traceId={}", name, context.getTraceId(), exception);
            ToolExecutionResult<Object> result = failure(name, "TOOL_EXECUTION_FAILED", "工具执行失败");
            auditService.record(tool.definition, context, result, startedAt);
            return result;
        }
    }

    /**
     * 查询当前已注册工具定义。
     *
     * @return 工具定义
     */
    public Map<String, ToolDefinition> definitions() {
        return tools.entrySet().stream().collect(java.util.stream.Collectors.toUnmodifiableMap(
                Map.Entry::getKey, item -> item.getValue().definition));
    }

    private ToolExecutionResult<Object> failure(String name, String errorCode, String warning) {
        return ToolExecutionResult.<Object>builder()
                .success(false)
                .status("FAILED")
                .source(name)
                .asOf(LocalDateTime.now())
                .warnings(warning)
                .errorCode(errorCode)
                .build();
    }

    private String limit(String value, int maxLength) {
        int safeLength = maxLength <= 0 ? 12000 : maxLength;
        return value.length() > safeLength ? value.substring(0, safeLength) : value;
    }

    private record RegisteredTool(ToolDefinition definition, ToolExecutor<?> executor) {
    }
}
// AI_GENERATE_END ---
