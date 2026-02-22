package online.mwang.stockTrading.jobs;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import online.mwang.stockTrading.services.TradeExecutionService;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.stereotype.Component;

/**
 * 收盘后任务Job
 * 执行时间: 每天 15:10
 * 任务内容: 撤销当日无效订单
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EndOfDayJob implements Job {

    private final TradeExecutionService tradeExecutionService;

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        log.info("========== 收盘后任务开始 (15:10) ==========");
        try {
            // 撤销当日无效订单
            Integer count = tradeExecutionService.cancelAllOrder();
            log.info("收盘后任务完成，共处理{}条无效订单", count);
        } catch (Exception e) {
            log.error("收盘后任务执行失败: {}", e.getMessage(), e);
            throw new JobExecutionException(e);
        }
        log.info("========== 收盘后任务结束 ==========");
    }
}
