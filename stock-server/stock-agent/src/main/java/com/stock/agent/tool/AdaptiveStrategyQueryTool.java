// AI_GENERATE_START --
package com.stock.agent.tool;

import com.stock.agent.model.AdaptiveStrategyFact;
import com.stock.agent.service.AgentFactQueryService;
import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 自适应策略生命周期和演化状态只读工具。
 *
 * @author mwangli
 * @since 2026-10-25
 */
@Component
@RequiredArgsConstructor
public class AdaptiveStrategyQueryTool {

    private final AgentFactQueryService factQueryService;
    private final AgentToolSupport toolSupport;

    /**
     * 查询策略生命周期、健康环节、生产与候选模型和近期演化事件。
     *
     * @return 自适应策略事实
     */
    @Tool(name = "queryAdaptiveStrategy", value = "查询自适应策略当前生命周期、运行模式、健康环节、生产与候选模型、近期演化事件、异常和自动交易暂停状态。适用于策略为何未推进、当前处于哪个阶段、模型如何演化等问题")
    public AdaptiveStrategyFact query() {
        return toolSupport.execute("queryAdaptiveStrategy", factQueryService::adaptiveStrategy);
    }
}
// AI_GENERATE_END --
