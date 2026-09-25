// AI_GENERATE_START -
package com.stock.tradingExecutor.job;

import com.stock.tradingExecutor.domain.dto.TradeExecutionBatchResponseDto;
import com.stock.tradingExecutor.service.RealTradingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 真实交易统一调度入口。
 * 任务默认禁用，且执行时仍受 LIVE_AUTO、券商会话和真实写入总门禁共同约束。
 *
 * @author mwangli
 * @since 2026-09-25
 */
@Slf4j
@Component("realTradingJob")
@RequiredArgsConstructor
public class RealTradingJob {

    private final RealTradingService realTradingService;

    /**
     * 执行无人值守候选买入检查。
     */
    public void executeAutoBuys() {
        TradeExecutionBatchResponseDto result = realTradingService.executeAutomaticBuys();
        log.info("无人值守候选买入检查结束: 执行数={}, 成功数={}",
                result.getItems().size(), result.getSuccessCount());
    }

    /**
     * 执行无人值守 T+1 退出检查。
     */
    public void checkT1Exits() {
        TradeExecutionBatchResponseDto result = realTradingService.executeAutomaticExits();
        log.info("无人值守 T+1 退出检查结束: 执行数={}, 成功数={}",
                result.getItems().size(), result.getSuccessCount());
    }
}
// AI_GENERATE_END -
