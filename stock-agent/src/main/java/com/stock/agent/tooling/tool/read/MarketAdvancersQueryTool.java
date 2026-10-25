// AI_GENERATE_START -
package com.stock.agent.tooling.tool.read;

import com.stock.agent.tooling.model.MarketAdvancersFact;
import com.stock.agent.tooling.port.ToolFactQueryPort;
import com.stock.agent.tooling.tool.AgentToolSupport;
import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 最近可用行情日涨幅榜只读工具。
 *
 * @author mwangli
 * @since 2026-10-25
 */
@Component
@RequiredArgsConstructor
public class MarketAdvancersQueryTool {

    private final ToolFactQueryPort factQueryPort;
    private final AgentToolSupport toolSupport;

    /**
     * 查询最近可用行情日的涨跌分布和上涨股票。
     *
     * @return 带数据日期的市场事实
     */
    @Tool(name = "queryMarketAdvancers", value = "查询最近可用行情日的上涨股票、涨跌分布和数据时间")
    public MarketAdvancersFact query() {
        return toolSupport.execute("queryMarketAdvancers", factQueryPort::marketAdvancers);
    }
}
// AI_GENERATE_END -
