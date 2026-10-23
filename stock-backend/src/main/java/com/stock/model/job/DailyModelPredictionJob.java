// AI_GENERATE_START----------
package com.stock.model.job;

import com.stock.data.service.TradingCalendarService;
import com.stock.model.dto.PredictionBackfillResponse;
import com.stock.model.service.ModelPredictionAnalysisService;
import com.stock.base.job.JobSkippedException;
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
     * 扫描最近交易日预测和评估缺口，并按顺序同步消费两个持久化队列。
     */
    public void runDailyPrediction() {
        LocalDate today = LocalDate.now(MARKET_ZONE);
        if (!tradingCalendarService.isConfirmedDate(today)) {
            throw new IllegalStateException("当日交易日历缺失或尚未确认: " + today);
        }
        if (!tradingCalendarService.isTradingDay(today)) {
            throw new JobSkippedException("当日休市，跳过模型预测: " + today);
        }
        PredictionBackfillResponse result = predictionAnalysisService.startScheduledPrediction();
        log.info("每日预测和评估统一队列任务完成: scanned={}, predictionQueued={}, evaluationQueued={}, message={}",
                result.getScannedTradingDays(), result.getPredictionQueuedCount(),
                result.getEvaluationQueuedCount(), result.getMessage());
    }
}
// AI_GENERATE_END----------
