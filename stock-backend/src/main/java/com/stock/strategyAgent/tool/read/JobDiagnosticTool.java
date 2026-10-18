// AI_GENERATE_START ---
package com.stock.strategyAgent.tool.read;

import com.stock.strategyAgent.application.JobExecutionQuery;
import com.stock.strategyAgent.application.JobExecutionSummary;
import com.stock.strategyAgent.tool.AgentToolSupport;
import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
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
public class JobDiagnosticTool {

    private final JobExecutionQuery jobExecutionQuery;
    private final AgentToolSupport toolSupport;

    /**
     * 查询固定任务最近一次脱敏运行摘要。
     *
     * @param request 查询请求
     * @return 任务运行摘要
     */
    @Tool(name = "queryJobDiagnostic", value = "查询固定任务最近一次脱敏运行摘要")
    public JobDiagnosticResult query(JobDiagnosticRequest request) {
        if (request == null || !StringUtils.hasText(request.jobName())) {
            throw new IllegalArgumentException("jobName 不能为空");
        }
        return toolSupport.execute("queryJobExecution", () -> {
            Optional<JobExecutionSummary> latest = jobExecutionQuery.getLatestRun(request.jobName().trim());
            return latest.map(item -> new JobDiagnosticResult(request.jobName().trim(), item.getStatus(),
                            item.getErrorMessage()))
                    .orElseGet(() -> new JobDiagnosticResult(request.jobName().trim(), "UNKNOWN", "没有运行记录"));
        });
    }

    /** 任务诊断请求。 */
    public record JobDiagnosticRequest(String jobName) {
    }

    /** 任务诊断结果。 */
    public record JobDiagnosticResult(String jobName, String status, String errorMessage) {
    }
}
// AI_GENERATE_END ---
