// AI_GENERATE_START -----
package com.stock.strategyAgent.strategy.engine;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.stock.strategyAgent.strategy.config.StrategyConfigService;
import org.springframework.stereotype.Component;

/**
 * 真实模型选股任务入口。
 * 由固定 Quartz Job 类调用，不允许在业务类上添加 @Scheduled。
 *
 * @author mwangli
 * @since 2026-09-25
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StrategyScheduler {

    private final StockSelector stockSelector;
    private final StrategyConfigService configService;

    /**
     * 执行 LSTM 与情感分析综合选股任务。
     */
    public void runStockSelection() {
        log.info("========== 开始执行选股任务 ==========");
        try {
            stockSelector.selectTopN(configService.getCurrentConfig().getTopN());
            log.info("========== 选股任务完成 ==========");
        } catch (Exception e) {
            log.error("选股任务执行失败", e);
        }
    }

}
// AI_GENERATE_END -----
