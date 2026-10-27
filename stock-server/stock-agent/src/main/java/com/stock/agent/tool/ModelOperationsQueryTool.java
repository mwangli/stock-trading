// AI_GENERATE_START ---
package com.stock.agent.tool;

import com.stock.agent.model.ModelOperationsFact;
import com.stock.agent.service.AgentFactQueryService;
import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 模型版本、训练和预测运维只读工具。
 *
 * @author mwangli
 * @since 2026-10-25
 */
@Component
@RequiredArgsConstructor
public class ModelOperationsQueryTool {

    private static final int DEFAULT_LIMIT = 10;
    private static final int MAX_LIMIT = 20;

    private final AgentFactQueryService factQueryService;
    private final AgentToolSupport toolSupport;

    /**
     * 按模式查询模型版本、训练运行、预测批次或预测能力。
     *
     * @param request 查询参数
     * @return 模型运维事实
     */
    @Tool(name = "queryModelOperations", value = "查询模型运维事实。mode=VERSIONS 查看模型版本和评估指标，TRAINING_RUNS 查看训练进度与失败原因，PREDICTION_BATCHES 查看预测批次，PREDICTION_ABILITY 查看指定或最新批次预测能力；limit 最大 20")
    public ModelOperationsFact query(ModelOperationsRequest request) {
        ModelOperationsMode mode = request == null || request.mode() == null
                ? ModelOperationsMode.VERSIONS : request.mode();
        String referenceId = request == null ? null : request.referenceId();
        int limit = normalizeLimit(request == null ? null : request.limit());
        return toolSupport.execute("queryModelOperations",
                () -> factQueryService.modelOperations(mode.name(), referenceId, limit));
    }

    private int normalizeLimit(Integer limit) {
        if (limit == null) {
            return DEFAULT_LIMIT;
        }
        return Math.max(1, Math.min(limit, MAX_LIMIT));
    }

    /** 模型运维查询模式。 */
    public enum ModelOperationsMode {
        VERSIONS,
        TRAINING_RUNS,
        PREDICTION_BATCHES,
        PREDICTION_ABILITY
    }

    /**
     * 模型运维查询请求。
     *
     * @param mode 查询模式，可为空
     * @param referenceId 预测批次标识，仅能力查询按需提供
     * @param limit 最大返回数量，可为空，范围 1 到 20
     */
    public record ModelOperationsRequest(ModelOperationsMode mode, String referenceId, Integer limit) {
    }
}
// AI_GENERATE_END ---