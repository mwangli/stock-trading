package online.mwang.stockTrading.jobs;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import online.mwang.stockTrading.services.TradeExecutionService;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.stereotype.Component;

/**
 * 下午交易时段Job
 * 执行时间: 每天 13:30
 * 任务内容: 止损检查和卖出
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AfternoonTradingJob implements Job {

    private final TradeExecutionService tradeExecutionService;

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        log.info("========== 下午交易时段任务开始 (13:30) ==========");
        try {
            // 执行止损检查
            tradeExecutionService.retrySellIfNeeded();
            log.info("下午止损检查完成");
        } catch (Exception e) {
            log.error("下午交易时段任务执行失败: {}", e.getMessage(), e);
            throw new JobExecutionException(e);
        }
        log.info("========== 下午交易时段任务结束 ==========");
    }
}
