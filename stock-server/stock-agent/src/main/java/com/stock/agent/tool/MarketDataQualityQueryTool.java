// AI_GENERATE_START --
package com.stock.agent.tool;

import com.stock.agent.model.MarketDataQualityFact;
import com.stock.agent.service.AgentFactQueryService;
import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 市场基础数据规模和完整性只读工具。
 *
 * @author mwangli
 * @since 2026-10-25
 */
@Component
@RequiredArgsConstructor
public class MarketDataQualityQueryTool {

    private final AgentFactQueryService factQueryService;
    private final AgentToolSupport toolSupport;

    /**
     * 查询股票数据规模、市场分布、字段完整率和缺失项。
     *
     * @return 市场数据质量事实
     */
    @Tool(name = "queryMarketDataQuality", value = "查询系统股票基础数据的记录规模、市场分布、字段完整率、缺失统计和整体质量。适用于数据是否齐全、为什么候选不足、市场数据覆盖情况等诊断问题")
    public MarketDataQualityFact query() {
        return toolSupport.execute("queryMarketDataQuality", factQueryService::marketDataQuality);
    }
}
// AI_GENERATE_END --