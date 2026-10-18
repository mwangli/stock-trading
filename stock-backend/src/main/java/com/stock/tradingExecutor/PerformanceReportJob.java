// AI_GENERATE_START- -
package com.stock.tradingExecutor;

import com.stock.tradingExecutor.service.PerformanceReportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 每日交易收益统计快照任务入口。
 *
 * @author mwangli
 * @since 2026-10-11
 */
@Slf4j
@Component("performanceReportJob")
@RequiredArgsConstructor
public class PerformanceReportJob {

    private final PerformanceReportService performanceReportService;

    /**
     * 交易日收盘后根据交易记录刷新收益统计快照。
     */
    public void refreshDailySnapshot() {
        performanceReportService.refreshDailySnapshot();
        log.info("[PerformanceReportJob] 每日收益统计快照刷新完成");
    }
}
// AI_GENERATE_END- -