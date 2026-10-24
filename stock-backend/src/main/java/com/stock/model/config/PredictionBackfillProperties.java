// AI_GENERATE_START-----
package com.stock.model.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 模型预测历史补缺配置。
 * 控制定时预测完成后向历史交易日检查和补充缺失预测批次的范围。
 *
 * @author mwangli
 * @since 2026-10-20
 */
@Data
@Component
@ConfigurationProperties(prefix = "models.lstm.prediction")
public class PredictionBackfillProperties {

    private static final int MAX_HISTORY_BACKFILL_TRADING_DAYS = 30;

    /**
     * 向历史回溯检查的最大交易日数量，0 表示关闭历史预测补缺，最大值为 30。
     */
    private int historyBackfillTradingDays = MAX_HISTORY_BACKFILL_TRADING_DAYS;

    /**
     * 预测基准日和评估目标日的最低行情覆盖率，取值范围为 0 至 1。
     */
    private double minPriceCoverageRate = 0.95D;

    /**
     * 获取经过边界约束的历史补缺交易日数量。
     *
     * @return 0 至 30 之间的历史回溯交易日数量
     */
    public int resolveHistoryBackfillTradingDays() {
        return Math.max(0, Math.min(historyBackfillTradingDays, MAX_HISTORY_BACKFILL_TRADING_DAYS));
    }

    /**
     * 获取经过边界约束的最低行情覆盖率。
     *
     * @return 0 至 1 之间的最低行情覆盖率
     */
    public double resolveMinPriceCoverageRate() {
        return Math.max(0D, Math.min(minPriceCoverageRate, 1D));
    }
}
// AI_GENERATE_END-----
