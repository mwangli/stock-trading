// AI_GENERATE_START-----
package com.stock.agent.tooling.tool.read;

import com.stock.agent.runtime.api.AgentModelProvider;
import com.stock.agent.runtime.api.AgentModelProviderStatus;
import com.stock.agent.tooling.tool.AgentToolSupport;
import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * Agent Provider 运行状态只读工具。
 *
 * @author mwangli
 * @since 2026-10-19
 */
@Component
@RequiredArgsConstructor
public class ProviderRuntimeQueryTool {

    private final ObjectProvider<AgentModelProvider> modelProvider;
    private final AgentToolSupport toolSupport;

    /**
     * 查询当前 Provider 的配置、最近运行状态和熔断状态。
     *
     * @return Provider 运行状态
     */
    @Tool(name = "queryProviderRuntime", value = "查询当前 Agent Provider 配置、运行状态和熔断状态")
    public ProviderRuntimeResult query() {
        return toolSupport.execute("queryProviderRuntime", () -> {
            AgentModelProviderStatus status = modelProvider.getObject().status();
            return new ProviderRuntimeResult(status.getProvider(), status.isDeepseekEnabled(),
                    status.isDeepseekConfigured(), status.getDeepseekStatus(), status.getDeepseekCircuitState(),
                    status.isOllamaEnabled(), status.isOllamaConfigured(), status.getOllamaStatus(),
                    status.getOllamaCircuitState());
        });
    }

    /** Provider 运行状态摘要。 */
    public record ProviderRuntimeResult(String provider, boolean deepseekEnabled,
                                        boolean deepseekConfigured, String deepseekStatus,
                                        String deepseekCircuitState, boolean ollamaEnabled,
                                        boolean ollamaConfigured, String ollamaStatus,
                                        String ollamaCircuitState) {
    }
}
// AI_GENERATE_END----
