// AI_GENERATE_START ---
package com.stock.agent.tool;

import com.stock.agent.model.JobOperationsFact;
import com.stock.agent.service.AgentFactQueryService;
import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 调度任务清单和运行历史只读工具。
 *
 * @author mwangli
 * @since 2026-10-25
 */
@Component
@RequiredArgsConstructor
public class JobOperationsQueryTool {

    private static final int DEFAULT_LIMIT = 10;
    private static final int MAX_LIMIT = 20;

    private final AgentFactQueryService factQueryService;
    private final AgentToolSupport toolSupport;

    /**
     * 查询任务配置状态或执行记录。
     *
     * @param request 查询参数
     * @return 调度任务事实
     */
    @Tool(name = "queryJobOperations", value = "查询统一调度中心的只读事实。mode=JOBS 查看任务清单、Cron、启用状态和最近运行，mode=RUNS 查看执行记录、耗时和错误；jobName 可筛选具体任务，limit 最大 20")
    public JobOperationsFact query(JobOperationsRequest request) {
        JobOperationsMode mode = request == null || request.mode() == null
                ? JobOperationsMode.JOBS : request.mode();
        String jobName = request == null ? null : request.jobName();
        int limit = normalizeLimit(request == null ? null : request.limit());
        return toolSupport.execute("queryJobOperations",
                () -> factQueryService.jobOperations(mode.name(), jobName, limit));
    }

    private int normalizeLimit(Integer limit) {
        if (limit == null) {
            return DEFAULT_LIMIT;
        }
        return Math.max(1, Math.min(limit, MAX_LIMIT));
    }

    /** 调度任务查询模式。 */
    public enum JobOperationsMode {
        JOBS,
        RUNS
    }

    /**
     * 调度任务查询请求。
     *
     * @param mode 查询模式，可为空
     * @param jobName 任务名称，可为空
     * @param limit 最大返回数量，可为空，范围 1 到 20
     */
    public record JobOperationsRequest(JobOperationsMode mode, String jobName, Integer limit) {
    }
}
// AI_GENERATE_END ---