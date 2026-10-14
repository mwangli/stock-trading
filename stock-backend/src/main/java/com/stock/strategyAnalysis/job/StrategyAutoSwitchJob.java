// AI_GENERATE_START -
package com.stock.strategyAnalysis.job;

import com.stock.dataCollector.domain.entity.ModelReferenceDailyBar;
import com.stock.dataCollector.domain.entity.ReferenceSeriesType;
import com.stock.dataCollector.persistence.ModelReferenceDailyBarRepository;
import com.stock.strategyAnalysis.engine.StrategySwitcher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 策略规则自动切换任务。
 * 读取已落库的市场参考序列，不发起外部行情请求，不执行交易写操作。
 *
 * @author mwangli
 * @since 2026-10-13
 */
@Slf4j
@Component("strategyAutoSwitchJob")
@RequiredArgsConstructor
public class StrategyAutoSwitchJob {

    private final ModelReferenceDailyBarRepository referenceBarRepository;
    private final StrategySwitcher strategySwitcher;

    /**
     * 使用最近两个交易日市场收盘价执行一次规则评估。
     * 数据不足、序列不完整或涨跌幅不可计算时保持当前模式。
     */
    public void runAutoSwitch() {
        List<ModelReferenceDailyBar> bars = referenceBarRepository
                .findTop100BySeriesTypeOrderByTradeDateDesc(ReferenceSeriesType.MARKET);
        List<ModelReferenceDailyBar> latestSeries = selectLatestCompleteSeries(bars);
        if (latestSeries.size() < 2) {
            log.warn("策略自动切换跳过：市场参考序列不足两个交易日");
            return;
        }
        ModelReferenceDailyBar latest = latestSeries.get(0);
        ModelReferenceDailyBar previous = latestSeries.get(1);
        BigDecimal latestClose = latest.getClosePrice();
        BigDecimal previousClose = previous.getClosePrice();
        if (latestClose == null || previousClose == null
                || previousClose.signum() <= 0) {
            log.warn("策略自动切换跳过：市场收盘价无效，seriesCode={}", latest.getSeriesCode());
            return;
        }
        double changePercent = latestClose.subtract(previousClose)
                .divide(previousClose, 8, java.math.RoundingMode.HALF_UP)
                .doubleValue() * 100D;
        log.info("策略自动切换市场评估: seriesCode={}, tradeDate={}, changePercent={}",
                latest.getSeriesCode(), latest.getTradeDate(), changePercent);
        strategySwitcher.autoSwitchByMarket(changePercent, latest.getTradeDate());
    }

    private List<ModelReferenceDailyBar> selectLatestCompleteSeries(List<ModelReferenceDailyBar> bars) {
        Map<String, List<ModelReferenceDailyBar>> grouped = new LinkedHashMap<>();
        for (ModelReferenceDailyBar bar : bars) {
            if (bar.getSeriesCode() != null) {
                grouped.computeIfAbsent(bar.getSeriesCode(), ignored -> new java.util.ArrayList<>()).add(bar);
            }
        }
        return grouped.values().stream()
                .filter(series -> series.size() >= 2)
                .findFirst()
                .orElseGet(List::of);
    }
}
// AI_GENERATE_END -
