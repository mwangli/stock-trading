// AI_GENERATE_START---------
package com.stock.agent.tooling.tool.read;
import com.stock.agent.tooling.port.ToolFactQueryPort;
import com.stock.agent.tooling.model.ModelOverviewFact;
import com.stock.agent.tooling.tool.AgentToolSupport;
import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 模型运维概览只读工具。
 *
 * @author mwangli
 * @since 2026-10-22
 */
@Component
@RequiredArgsConstructor
public class ModelOverviewQueryTool {

    private final ToolFactQueryPort factQueryPort;
    private final AgentToolSupport toolSupport;

    /**
     * 查询当前激活模型、最近训练状态和模型健康状态。
     *
     * @return 模型运维概览
     */
    @Tool(name = "queryModelOverview", value = "查询当前激活模型、最近训练状态和模型健康状态")
    public ModelOverviewResult query() {
        return toolSupport.execute("queryModelOverview", () -> {
            ModelOverviewFact overview = factQueryPort.modelOverview();
            return new ModelOverviewResult(overview.modelName(), overview.activeVersionId(),
                    overview.activeVersion(), overview.activeVersionStatus(), overview.activeModelHealthy(),
                    overview.trainingRunning(), overview.latestTrainingRunId(), overview.latestTrainingStatus(),
                    overview.latestTrainingCreatedAt(), overview.asOf());
        });
    }

    /** 模型运维概览结果。 */
    public record ModelOverviewResult(String modelName, String activeVersionId, String activeVersion,
                                      String activeVersionStatus, boolean activeModelHealthy,
                                      boolean trainingRunning, String latestTrainingRunId,
                                      String latestTrainingStatus, LocalDateTime latestTrainingCreatedAt,
                                      LocalDateTime asOf) {
    }
}
// AI_GENERATE_END--------
