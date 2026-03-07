package com.stock.strategyAnalysis.intraday;

import com.stock.strategyAnalysis.entity.MinuteBar;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 布林带计算器
 * 检测上轨突破情况
 */
@Slf4j
@Component
public class BollingerCalculator {

    private static final int DEFAULT_PERIOD = 20;    // 默认周期
    private static final double DEFAULT_MULTIPLIER = 2.0; // 默认标准差倍数

    /**
     * 计算布林带
     *
     * @param bars 分钟K线列表
     * @param period 周期
     * @param multiplier 标准差倍数
     * @return BollingerBands对象
     */
    public BollingerBands calculate(List<MinuteBar> bars, int period, double multiplier) {
        if (bars == null || bars.size() < period) {
            log.warn("K线数据不足，无法计算布林带");
            return null;
        }

        // 计算中轨（移动平均）
        double sum = 0;
        for (int i = bars.size() - period; i < bars.size(); i++) {
            sum += bars.get(i).getClosePrice();
        }
        double middleBand = sum / period;

        // 计算标准差
        double sumSquaredDiff = 0;
        for (int i = bars.size() - period; i < bars.size(); i++) {
            double diff = bars.get(i).getClosePrice() - middleBand;
            sumSquaredDiff += diff * diff;
        }
        double stdDev = Math.sqrt(sumSquaredDiff / period);

        // 计算上下轨
        double upperBand = middleBand + multiplier * stdDev;
        double lowerBand = middleBand - multiplier * stdDev;

        return new BollingerBands(middleBand, upperBand, lowerBand);
    }

    /**
     * 使用默认参数计算布林带
     */
    public BollingerBands calculate(List<MinuteBar> bars) {
        return calculate(bars, DEFAULT_PERIOD, DEFAULT_MULTIPLIER);
    }

    /**
     * 检查上轨突破
     * 条件：当前价 > 上轨 × 阈值 AND 连续N分钟在上轨之上
     *
     * @param bars 分钟K线列表
     * @param threshold 突破阈值（如1.005表示突破0.5%）
     * @param consecutiveBars 连续突破要求的K线数
     * @return 是否突破
     */
    public boolean checkUpperBandBreakout(List<MinuteBar> bars, double threshold, int consecutiveBars) {
        if (bars == null || bars.size() < DEFAULT_PERIOD + consecutiveBars) {
            return false;
        }

        BollingerBands bands = calculate(bars);
        if (bands == null) {
            return false;
        }

        double breakoutPrice = bands.upperBand * threshold;

        // 检查连续N分钟是否都在突破价之上
        for (int i = bars.size() - consecutiveBars; i < bars.size(); i++) {
            if (bars.get(i).getClosePrice() <= breakoutPrice) {
                return false;
            }
        }

        log.info("布林带突破触发: 上轨={}, 突破价={}, 连续{}分钟突破", 
                bands.upperBand, breakoutPrice, consecutiveBars);
        return true;
    }

    /**
     * 布林带数据结构
     */
    public static class BollingerBands {
        public final double middleBand;
        public final double upperBand;
        public final double lowerBand;

        public BollingerBands(double middleBand, double upperBand, double lowerBand) {
            this.middleBand = middleBand;
            this.upperBand = upperBand;
            this.lowerBand = lowerBand;
        }
    }
}