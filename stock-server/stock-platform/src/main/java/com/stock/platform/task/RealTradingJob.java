// AI_GENERATE_START----------
package com.stock.platform.task;

import com.stock.trading.dto.TradeExecutionBatchResponseDto;
import com.stock.trading.service.RealTradingService;
import com.stock.platform.repository.JobConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 真实交易统一调度入口。
 * 任务启停是自动交易唯一控制，订单执行仍依赖券商会话和交易事实。
 *
 * @author mwangli
 * @since 2026-09-25
 */
@Slf4j
@Component("realTradingJob")
@RequiredArgsConstructor
public class RealTradingJob {

    private final RealTradingService realTradingService;
    private final JobConfigRepository jobConfigRepository;

    /**
     * 执行上午分阶段卖出。
     */
    public void executeMorningSell() {
        TradeExecutionBatchResponseDto result = realTradingService.executeMorningSell(
                () -> isEnabled("realTradingMorningSell"));
        log.info("上午分阶段卖出结束: 执行数={}, 成功数={}",
                result.getItems().size(), result.getSuccessCount());
    }

    /**
     * 执行下午模型前三名竞争买入。
     */
    public void executeAfternoonBuy() {
        TradeExecutionBatchResponseDto result = realTradingService.executeAfternoonBuy(
                () -> isEnabled("realTradingAfternoonBuy"));
        log.info("下午模型候选竞争买入结束: 执行数={}, 成功数={}",
                result.getItems().size(), result.getSuccessCount());
    }

    private boolean isEnabled(String jobName) {
        return jobConfigRepository.findByJobName(jobName)
                .map(config -> Integer.valueOf(1).equals(config.getStatus()))
                .orElse(false);
    }
}
// AI_GENERATE_END----------
