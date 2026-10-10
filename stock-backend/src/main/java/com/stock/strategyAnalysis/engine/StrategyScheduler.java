// AI_GENERATE_START --
package com.stock.strategyAnalysis.engine;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 真实模型选股任务入口。
 * 只能通过 JobConfig 和 JobSchedulerService 调度，不允许添加 @Scheduled。
 *
 * @author mwangli
 * @since 2026-09-25
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StrategyScheduler {

    private final StockSelector stockSelector;

    /**
     * 执行 LSTM 与情感分析综合选股任务。
     */
    public void runStockSelection() {
        log.info("========== 开始执行选股任务 ==========");
        try {
            stockSelector.selectTopN(10);
            log.info("========== 选股任务完成 ==========");
        } catch (Exception e) {
            log.error("选股任务执行失败", e);
        }
    }

}
// AI_GENERATE_END --
