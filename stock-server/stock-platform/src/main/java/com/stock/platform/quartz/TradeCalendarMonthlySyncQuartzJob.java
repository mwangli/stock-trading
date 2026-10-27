// AI_GENERATE_START--------
package com.stock.platform.quartz;

import com.stock.platform.service.JobExecutionService;
import com.stock.platform.support.InterruptibleQuartzJob;
import com.stock.platform.support.QuartzJobSupport;

import com.stock.data.service.TradingCalendarMaintenanceService;
import com.stock.data.service.TradingCalendarMaintenanceService.CalendarMaintenanceResult;
import lombok.extern.slf4j.Slf4j;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.JobExecutionContext;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 交易日历月度滚动同步 Quartz 任务。
 *
 * @author mwangli
 * @since 2026-10-13
 */
@Slf4j
@DisallowConcurrentExecution
public class TradeCalendarMonthlySyncQuartzJob extends InterruptibleQuartzJob {

    @Autowired
    private JobExecutionService jobExecutionService;

    @Autowired
    private TradingCalendarMaintenanceService maintenanceService;

    /**
     * 执行交易日历回补、确认和未来一年滚动填充。
     *
     * @param context Quartz 执行上下文
     */
    @Override
    protected void executeInterruptibly(JobExecutionContext context) {
        jobExecutionService.execute("tradeCalendarMonthlySync", QuartzJobSupport.triggerSource(context),
                QuartzJobSupport.triggeredBy(context), QuartzJobSupport.runId(context),
                context.getScheduledFireTime(), this::maintainCalendar);
    }

    private void maintainCalendar() {
        CalendarMaintenanceResult result = maintenanceService.maintainRollingCalendar();
        log.info("交易日历月度任务完成: range={}~{}, inserted={}, confirmed={}, unverified={}, sourceVersion={}",
                result.startDate(), result.endDate(), result.insertedCount(), result.confirmedCount(),
                result.unverifiedCount(), result.sourceVersion());
    }
}
// AI_GENERATE_END--------
