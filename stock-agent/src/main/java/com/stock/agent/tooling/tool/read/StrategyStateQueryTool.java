// AI_GENERATE_START--------
package com.stock.agent.tooling.tool.read;

import com.stock.agent.tooling.port.ToolFactQueryPort;
import com.stock.agent.tooling.model.StrategyStateFact;
import com.stock.agent.tooling.tool.AgentToolSupport;
import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 当前策略状态只读工具。
 *
 * @author mwangli
 * @since 2026-10-17
 */
@Component
@RequiredArgsConstructor
public class StrategyStateQueryTool {

    private final ToolFactQueryPort factQueryPort;
    private final AgentToolSupport toolSupport;

    /**
     * 查询当前策略开关、模式和配置版本。
     *
     * @return 策略状态
     */
    @Tool(name = "queryStrategyState", value = "查询当前策略开关、模式和配置版本")
    public StrategyStateResult query() {
        return toolSupport.execute("queryStrategyState", () -> {
            StrategyStateFact state = factQueryPort.strategyState();
            return new StrategyStateResult(state.enabled(), state.currentMode(),
                    state.configVersion(), state.autoSwitchEnabled(), state.disabledIndicators());
        });
    }

    /** 策略状态脱敏结果。 */
    public record StrategyStateResult(boolean enabled, String currentMode, String configVersion,
                                      boolean autoSwitchEnabled, java.util.List<String> disabledIndicators) {
    }
}
// AI_GENERATE_END-------
