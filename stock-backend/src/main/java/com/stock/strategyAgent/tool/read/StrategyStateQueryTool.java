// AI_GENERATE_START -
package com.stock.strategyAgent.tool.read;

import com.stock.strategyAgent.strategy.config.StrategyStateManager;
import com.stock.strategyAgent.strategy.domain.dto.StrategyStateDto;
import com.stock.strategyAgent.tool.ToolDefinition;
import com.stock.strategyAgent.tool.ToolGateway;
import com.stock.strategyAgent.tool.ToolPermission;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;

/**
 * 当前策略状态只读工具。
 *
 * @author mwangli
 * @since 2026-10-17
 */
@Component
@RequiredArgsConstructor
public class StrategyStateQueryTool implements InitializingBean {

    private final ToolGateway toolGateway;
    private final StrategyStateManager strategyStateManager;

    /**
     * 注册策略状态工具。
     */
    @Override
    public void afterPropertiesSet() {
        toolGateway.register(ToolDefinition.builder()
                        .name("queryStrategyState")
                        .version("v1")
                        .description("查询当前策略开关、模式和配置版本")
                        .requiredPermission(ToolPermission.READ_ONLY)
                        .readOnly(true)
                        .maxResultSize(3000)
                        .timeoutMs(3000L)
                        .build(),
                (input, context) -> query());
    }

    private StrategyStateResult query() {
        StrategyStateDto state = strategyStateManager.getCurrentState();
        return new StrategyStateResult(state.isEnabled(), state.getCurrentMode().name(),
                state.getConfigVersion(), state.isAutoSwitchEnabled(), state.getDisabledIndicators());
    }

    /** 策略状态脱敏结果。 */
    public record StrategyStateResult(boolean enabled, String currentMode, String configVersion,
                                      boolean autoSwitchEnabled, java.util.List<String> disabledIndicators) {
    }
}
// AI_GENERATE_END -
