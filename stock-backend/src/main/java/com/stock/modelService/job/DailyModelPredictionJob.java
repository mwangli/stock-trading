// AI_GENERATE_START --
package com.stock.modelService.job;

import com.stock.dataCollector.service.TradingCalendarService;
import com.stock.modelService.domain.dto.ModelPredictionAnalysisDto;
import com.stock.modelService.service.ModelPredictionAnalysisService;
import com.stock.tradingExecutor.job.JobSkippedException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;

/**
 * 每日模型预测和实际结果评估业务任务。
 * 由统一 Quartz 调度中心触发，不直接管理调度生命周期。
 *
 * @author mwangli
 * @since 2026-10-12
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DailyModelPredictionJob {

    private static final ZoneId MARKET_ZONE = ZoneId.of("Asia/Shanghai");

    private final ModelPredictionAnalysisService predictionAnalysisService;
    private final TradingCalendarService tradingCalendarService;

    /**
     * 使用当前激活模型同步执行每日全市场预测。
     */
    public void runDailyPrediction() {
        LocalDate today = LocalDate.now(MARKET_ZONE);
        if (!tradingCalendarService.isConfirmedDate(today)) {
            throw new IllegalStateException("当日交易日历缺失或尚未确认: " + today);
        }
        if (!tradingCalendarService.isTradingDay(today)) {
            throw new JobSkippedException("当日休市，跳过模型预测: " + today);
        }
        ModelPredictionAnalysisDto.OperationResponse result = predictionAnalysisService.startScheduledPrediction();
        log.info("每日全市场预测任务完成: batchId={}, status={}, message={}",
                result.getBatchId(), result.getStatus(), result.getMessage());
    }

    /**
     * 扫描并刷新所有仍有待评估明细的预测批次。
     */
    public void refreshPredictionEvaluations() {
        predictionAnalysisService.evaluatePendingBatches();
    }
}
// AI_GENERATE_END --
