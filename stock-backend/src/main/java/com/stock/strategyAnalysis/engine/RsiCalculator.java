package com.stock.strategyAnalysis.engine;

import com.stock.strategyAnalysis.domain.vo.MinuteBar;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * RSI 指标计算器
 * 计算 RSI 超买指标
 */
@Slf4j
@Component
public class RsiCalculator {

    private static final int DEFAULT_PERIOD = 14;

    /**
     * 计算 RSI 值
     *
     * @param bars 分钟K线列表（按时间升序）
     * @param period RSI周期（默认14分钟）
     * @return RSI值 [0-100]
     */
    public double calculate(List<MinuteBar> bars, int period) {
        if (bars == null || bars.size() < period + 1) {
            log.warn("K线数据不足，无法计算RSI");
            return 50; // 返回中性值
        }

        double sumGain = 0;
        double sumLoss = 0;

        // 计算最近period根K线的涨跌
        for (int i = bars.size() - period; i < bars.size(); i++) {
            double change = bars.get(i).getClosePrice() - bars.get(i - 1).getClosePrice();
            if (change > 0) {
                sumGain += change;
            } else {
                sumLoss += Math.abs(change);
            }
        }

        // 计算平均涨跌
        double avgGain = sumGain / period;
        double avgLoss = sumLoss / period;

        // 计算 RSI
        if (avgLoss == 0) {
            return 100; // 全部上涨
        }

        double rs = avgGain / avgLoss;
        double rsi = 100 - (100 / (1 + rs));

        log.debug("RSI计算: avgGain={}, avgLoss={}, RSI={}", avgGain, avgLoss, rsi);
        return rsi;
    }

    /**
     * 使用默认周期计算 RSI
     */
    public double calculate(List<MinuteBar> bars) {
        return calculate(bars, DEFAULT_PERIOD);
    }

    /**
     * 检查是否超买
     *
     * @param rsiValue RSI值
     * @param threshold 超买阈值（默认75）
     * @return 是否超买
     */
    public boolean isOverbought(double rsiValue, double threshold) {
        return rsiValue > threshold;
    }

    /**
     * 检查 RSI 超买卖出条件
     * 条件：RSI > 阈值 AND 当前价 > 开盘价 × 1.02（至少涨2%）
     *
     * @param bars 分钟K线列表
     * @param threshold 超买阈值
     * @param openPrice 当日开盘价
     * @return 是否触发卖出
     */
    public boolean checkSellCondition(List<MinuteBar> bars, double threshold, double openPrice) {
        if (bars == null || bars.isEmpty()) {
            return false;
        }

        double rsi = calculate(bars);
        double currentPrice = bars.get(bars.size() - 1).getClosePrice();

        boolean isOverbought = isOverbought(rsi, threshold);
        boolean hasMinGain = currentPrice > openPrice * 1.02;

        boolean triggered = isOverbought && hasMinGain;

        if (triggered) {
            log.info("RSI超买触发: RSI={}, 阈值={}, 当前价={}, 开盘价={}",
                    rsi, threshold, currentPrice, openPrice);
        }

        return triggered;
    }
}
