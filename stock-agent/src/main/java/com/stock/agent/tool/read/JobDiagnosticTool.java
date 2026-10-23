// AI_GENERATE_START----------
package com.stock.agent.tool.read;

import com.stock.agent.client.BackendFactsClient;
import com.stock.agent.client.dto.JobDiagnosticFact;
import com.stock.agent.tool.AgentToolSupport;
import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;


/**
 * 固定任务诊断只读工具。
 *
 * @author mwangli
 * @since 2026-10-17
 */
@Component
@RequiredArgsConstructor
public class JobDiagnosticTool {

    private final BackendFactsClient backendFactsClient;
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
        return toolSupport.execute("queryJobDiagnostic", () -> {
            JobDiagnosticFact fact = backendFactsClient.jobDiagnostic(request.jobName().trim());
            return new JobDiagnosticResult(fact.jobName(), fact.status(), fact.errorMessage());
        });
    }

    /** 任务诊断请求。 */
    public record JobDiagnosticRequest(String jobName) {
    }

    /** 任务诊断结果。 */
    public record JobDiagnosticResult(String jobName, String status, String errorMessage) {
    }
}
// AI_GENERATE_END----------
