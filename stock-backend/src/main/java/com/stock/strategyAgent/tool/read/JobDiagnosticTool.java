// AI_GENERATE_START --
package com.stock.strategyAgent.tool.read;

import com.stock.strategyAgent.application.JobExecutionQuery;
import com.stock.strategyAgent.application.JobExecutionSummary;
import com.stock.strategyAgent.tool.ToolDefinition;
import com.stock.strategyAgent.tool.ToolGateway;
import com.stock.strategyAgent.tool.ToolPermission;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Optional;

/**
 * 固定任务诊断只读工具。
 *
 * @author mwangli
 * @since 2026-10-17
 */
@Component
@RequiredArgsConstructor
public class JobDiagnosticTool implements InitializingBean {

    private final ToolGateway toolGateway;
    private final JobExecutionQuery jobExecutionQuery;

    /**
     * 注册任务诊断工具。
     */
    @Override
    public void afterPropertiesSet() {
        toolGateway.register(ToolDefinition.builder()
                        .name("queryJobExecution")
                        .version("v1")
                        .description("查询固定任务最近一次脱敏运行摘要")
                        .requiredPermission(ToolPermission.READ_ONLY)
                        .readOnly(true)
                        .maxResultSize(4000)
                        .timeoutMs(3000L)
                        .build(),
                (input, context) -> query(input));
    }

    private JobDiagnosticResult query(Object input) {
        if (!(input instanceof JobDiagnosticRequest request) || !StringUtils.hasText(request.jobName())) {
            throw new IllegalArgumentException("jobName 不能为空");
        }
        Optional<JobExecutionSummary> latest = jobExecutionQuery.getLatestRun(request.jobName().trim());
        return latest.map(item -> new JobDiagnosticResult(request.jobName().trim(), item.getStatus(),
                        item.getErrorMessage()))
                .orElseGet(() -> new JobDiagnosticResult(request.jobName().trim(), "UNKNOWN", "没有运行记录"));
    }

    /** 任务诊断请求。 */
    public record JobDiagnosticRequest(String jobName) {
    }

    /** 任务诊断结果。 */
    public record JobDiagnosticResult(String jobName, String status, String errorMessage) {
    }
}
// AI_GENERATE_END --
