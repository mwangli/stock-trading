// AI_GENERATE_START -
package com.stock.agent.knowledge.rag.source;

import com.stock.agent.integration.client.BackendInternalApiClient;
import com.stock.agent.integration.dto.BackendModelOperationsResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 从 Backend 模型运维记录中提取已治理的研究资料摘要。
 *
 * @author mwangli
 * @since 2026-10-25
 */
@Component
@RequiredArgsConstructor
public class ModelResearchBusinessRagSource implements BusinessRagSource {

    private final BackendInternalApiClient backendClient;

    /**
     * 返回模型研究来源名称。
     *
     * @return 来源名称
     */
    @Override
    public String sourceName() {
        return "modelVersionResearch";
    }

    /**
     * 查询最近模型版本和离线指标摘要。
     *
     * @return 单份共享研究资料文档
     */
    @Override
    public List<BusinessRagSourceDocument> collect() {
        BackendModelOperationsResponse response = backendClient.modelOperations("VERSIONS", null, 20);
        StringBuilder content = new StringBuilder("# 模型版本研究资料\n\n")
                .append("资料说明：").append(value(response.summary())).append('\n')
                .append("记录总数：").append(response.total()).append("\n\n")
                .append("## 最近模型版本与评估摘要\n");
        if (response.items() == null || response.items().isEmpty()) {
            content.append("- 暂无模型版本记录\n");
        } else {
            response.items().forEach(item -> content.append("- ").append(value(item)).append('\n'));
        }
        return List.of(new BusinessRagSourceDocument(
                "RESEARCH_MATERIAL",
                "model-version-research",
                content.toString(),
                List.of(),
                List.of("研究资料", "模型评估", "模型版本"),
                "SHARED",
                "system",
                response.asOf()));
    }

    private String value(Object value) {
        return value == null ? "未知" : String.valueOf(value);
    }
}
// AI_GENERATE_END -
