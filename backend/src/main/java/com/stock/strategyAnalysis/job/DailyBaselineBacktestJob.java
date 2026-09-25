// AI_GENERATE_START --
package com.stock.strategyAnalysis.job;

import com.stock.strategyAnalysis.domain.dto.BacktestDataAuditDto;
import com.stock.strategyAnalysis.domain.dto.BacktestResultDto;
import com.stock.strategyAnalysis.domain.dto.DailyBaselineBacktestRequestDto;
import com.stock.strategyAnalysis.domain.entity.BacktestBaselineType;
import com.stock.strategyAnalysis.service.BacktestDataAuditService;
import com.stock.strategyAnalysis.service.BacktestExecutionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * 日线 T+1 基准回测调度入口。
 * 方法保持无参 public 形式，由统一 JobSchedulerService 反射调用。
 *
 * @author mwangli
 * @since 2026-09-25
 */
@Slf4j
@Component("dailyBaselineBacktestJob")
@RequiredArgsConstructor
public class DailyBaselineBacktestJob {

    private final BacktestDataAuditService backtestDataAuditService;
    private final BacktestExecutionService backtestExecutionService;

    /**
     * 使用数据库最新日线日期执行最近五年的动量固定成交基准。
     * 当前任务只允许产生降级回测结果，默认调度配置保持禁用。
     */
    public void runFiveYearMomentumBaseline() {
        LocalDate today = LocalDate.now();
        BacktestDataAuditDto discovery = backtestDataAuditService.audit(today.minusYears(6), today);
        LocalDate endDate = discovery.getLatestDailyDate();
        if (endDate == null) {
            throw new IllegalStateException("没有可用于定时基准回测的日线数据");
        }
        DailyBaselineBacktestRequestDto request = DailyBaselineBacktestRequestDto.builder()
                .startDate(endDate.minusYears(5))
                .endDate(endDate)
                .baselineType(BacktestBaselineType.MOMENTUM)
                .allowDegradedDailyBaseline(true)
                .build();
        BacktestResultDto result = backtestExecutionService.runDailyBaseline(request);
        log.info("[DailyBaselineBacktestJob] 五年动量基准完成: resultId={}, trades={}, grossReturn={}, netReturn5000={}",
                result.getResultId(), result.getTotalTrades(), result.getTotalReturn(), result.getNetReturn5000());
    }
}
// AI_GENERATE_END --
