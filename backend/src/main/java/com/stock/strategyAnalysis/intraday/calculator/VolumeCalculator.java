package com.stock.strategyAnalysis.intraday.calculator;

import com.stock.strategyAnalysis.entity.MinuteBar;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 成交量计算器
 * 检测量价背离情况
 */
@Slf4j
@Component
public class VolumeCalculator {

    private static final int PRICE_WINDOW = 5;  // 价格创新高窗口
    private static final int VOLUME_WINDOW = 5; // 成交量比较窗口

    /**
     * 计算成交量比率
     * 最近1分钟成交量 / 前5分钟均量
     *
     * @param bars 分钟K线列表
     * @return 成交量比率
     */
    public double calculateVolumeRatio(List<MinuteBar> bars) {
        if (bars == null || bars.size() < VOLUME_WINDOW + 1) {
            log.warn("K线数据不足，无法计算成交量比率");
            return 1.0;
        }

        // 最近1分钟成交量
        long lastVolume = bars.get(bars.size() - 1).getVolume();

        // 前5分钟平均成交量
        long sumVolume = 0;
        for (int i = bars.size() - VOLUME_WINDOW - 1; i < bars.size() - 1; i++) {
            sumVolume += bars.get(i).getVolume();
        }
        double avgVolume = sumVolume / (double) VOLUME_WINDOW;

        if (avgVolume == 0) {
            return 1.0;
        }

        return lastVolume / avgVolume;
    }

    /**
     * 检查是否创新高
     * 当前价是最近N分钟最高
     *
     * @param bars 分钟K线列表
     * @param window 窗口大小
     * @return 是否创新高
     */
    public boolean isNewHigh(List<MinuteBar> bars, int window) {
        if (bars == null || bars.size() < window) {
            return false;
        }

        double currentPrice = bars.get(bars.size() - 1).getClosePrice();
        
        for (int i = bars.size() - window; i < bars.size() - 1; i++) {
            if (bars.get(i).getClosePrice() >= currentPrice) {
                return false;
            }
        }

        return true;
    }

    /**
     * 检查成交量背离
     * 条件：价格创新高 AND 成交量萎缩
     *
     * @param bars 分钟K线列表
     * @param shrinkThreshold 萎缩阈值（默认0.8）
     * @return 是否出现量价背离
     */
    public boolean checkVolumeDivergence(List<MinuteBar> bars, double shrinkThreshold) {
        if (bars == null || bars.size() < VOLUME_WINDOW + 1) {
            return false;
        }

        // 检查是否价格创新高
        boolean isPriceNewHigh = isNewHigh(bars, PRICE_WINDOW);
        
        // 检查成交量是否萎缩
        double volumeRatio = calculateVolumeRatio(bars);
        boolean isVolumeShrink = volumeRatio < shrinkThreshold;

        boolean diverged = isPriceNewHigh && isVolumeShrink;
        
        if (diverged) {
            log.info("成交量背离触发: 价格创新高={}, 成交量比率={}", isPriceNewHigh, volumeRatio);
        }

        return diverged;
    }
}