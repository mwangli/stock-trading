// AI_GENERATE_START-------
package com.stock.agent.tool.read;
import com.stock.model.dto.ModelOverviewResponse;
import com.stock.model.dto.ModelTrainingRunResponse;
import com.stock.model.dto.ModelVersionResponse;
import com.stock.model.service.ModelOperationsQueryService;
import com.stock.agent.tool.AgentToolSupport;
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

    private final ModelOperationsQueryService modelOperationsQueryService;
    private final AgentToolSupport toolSupport;

    /**
     * 查询当前激活模型、最近训练状态和模型健康状态。
     *
     * @return 模型运维概览
     */
    @Tool(name = "queryModelOverview", value = "查询当前激活模型、最近训练状态和模型健康状态")
    public ModelOverviewResult query() {
        return toolSupport.execute("queryModelOverview", () -> {
            ModelOverviewResponse overview = modelOperationsQueryService.getOverview();
            ModelVersionResponse active = overview.getActiveVersion();
            ModelTrainingRunResponse training = overview.getLatestTrainingRun();
            return new ModelOverviewResult(overview.getModelName(),
                    active == null ? null : active.getId(),
                    active == null ? null : active.getModelVersion(),
                    active == null ? null : active.getStatus(),
                    overview.isActiveModelHealthy(), overview.isTrainingRunning(),
                    training == null ? null : training.getId(),
                    training == null ? null : training.getStatus(),
                    training == null ? null : training.getCreatedAt(), LocalDateTime.now());
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
// AI_GENERATE_END-------
