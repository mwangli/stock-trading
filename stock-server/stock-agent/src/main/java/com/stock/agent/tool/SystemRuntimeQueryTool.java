// AI_GENERATE_START ---
package com.stock.agent.tool;

import com.stock.agent.model.SystemRuntimeFact;
import com.stock.agent.service.AgentFactQueryService;
import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Backend 系统运行和只读控制状态工具。
 *
 * @author mwangli
 * @since 2026-10-25
 */
@Component
@RequiredArgsConstructor
public class SystemRuntimeQueryTool {

    private final AgentFactQueryService factQueryService;
    private final AgentToolSupport toolSupport;

    /**
     * 查询 Backend 健康、启动同步和紧急停止状态。
     *
     * @return 系统运行事实
     */
    @Tool(name = "querySystemRuntime", value = "查询 Backend 健康状态、节点、启动数据同步开关、自动交易暂停和紧急停止信息。适用于系统是否正常、启动同步是否启用、自动交易是否被暂停等运行诊断问题")
    public SystemRuntimeFact query() {
        return toolSupport.execute("querySystemRuntime", factQueryService::systemRuntime);
    }
}
// AI_GENERATE_END ---
