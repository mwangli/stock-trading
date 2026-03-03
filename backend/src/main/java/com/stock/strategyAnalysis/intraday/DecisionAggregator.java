package com.stock.strategyAnalysis.intraday;

import com.stock.strategyAnalysis.config.StrategyConfigService;
import com.stock.strategyAnalysis.entity.MinuteBar;
import com.stock.strategyAnalysis.entity.StrategyConfig;
import com.stock.strategyAnalysis.entity.StrategyScore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 决策聚合器
 * 综合多个指标计算最终卖出决策
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DecisionAggregator {

    private final TrailingStopCalculator trailingStopCalculator;
    private final RsiCalculator rsiCalculator;
    private final VolumeCalculator volumeCalculator;
    private final BollingerCalculator bollingerCalculator;
    private final StrategyConfigService configService;

    /**
     * 计算综合策略得分
     *
     * @param stockCode 股票代码
     * @param bars 分钟K线列表
     * @param currentPrice 当前价格
     * @param openPrice 开盘价
     * @return 策略得分
     */
    public StrategyScore calculateScore(String stockCode, List<MinuteBar> bars, 
            double currentPrice, double openPrice) {
        
        StrategyConfig config = configService.getCurrentConfig();
        
        // 计算各指标
        boolean trailingStopTriggered = trailingStopCalculator.checkTrigger(stockCode, currentPrice, config);
        double rsiValue = rsiCalculator.calculate(bars);
        double volumeRatio = volumeCalculator.calculateVolumeRatio(bars);
        boolean bollingerBreakout = bollingerCalculator.checkUpperBandBreakout(
                bars, config.getBollingerBreakoutThreshold(), 3);
        
        // 计算综合得分
        int totalScore = calculateTotalScore(
                trailingStopTriggered, rsiValue, volumeRatio, bollingerBreakout, config);
        
        // 计算动态阈值
        int dynamicThreshold = calculateDynamicThreshold(currentPrice, openPrice, config);
        
        return StrategyScore.builder()
                .stockCode(stockCode)
                .trailingStopTriggered(trailingStopTriggered)
                .rsiValue(rsiValue)
                .volumeRatio(volumeRatio)
                .bollingerBreakout(bollingerBreakout)
                .totalScore(totalScore)
                .dynamicThreshold(dynamicThreshold)
                .calculateTime(LocalDateTime.now())
                .build();
    }

    /**
     * 计算综合得分
     * 总分 = Σ(指标触发状态 × 指标权重)
     */
    private int calculateTotalScore(boolean trailingStopTriggered, double rsiValue,
            double volumeRatio, boolean bollingerBreakout, StrategyConfig config) {
        
        int score = 0;
        
        // 移动止损（40%权重）
        if (trailingStopTriggered) {
            score += (int) (config.getTrailingStopWeight() * 100);
        }
        
        // RSI超买（20%权重）
        if (rsiCalculator.isOverbought(rsiValue, config.getRsiOverboughtThreshold())) {
            score += (int) (config.getRsiWeight() * 100);
        }
        
        // 成交量背离（20%权重）
        if (volumeCalculator.checkVolumeDivergence(null, config.getVolumeShrinkThreshold())) {
            score += (int) (config.getVolumeWeight() * 100);
        }
        
        // 布林带突破（20%权重）
        if (bollingerBreakout) {
            score += (int) (config.getBollingerWeight() * 100);
        }
        
        return score;
    }

    /**
     * 计算动态阈值
     * 根据当日涨幅调整卖出阈值
     */
    private int calculateDynamicThreshold(double currentPrice, double openPrice, StrategyConfig config) {
        double gainPercent = (currentPrice - openPrice) / openPrice * 100;
        
        if (gainPercent > 3) {
            return config.getHighReturnThreshold(); // 50%
        } else if (gainPercent > 1) {
            return config.getNormalReturnThreshold(); // 60%
        } else if (gainPercent > 0) {
            return config.getLowReturnThreshold(); // 70%
        } else {
            return config.getLossReturnThreshold(); // 80%
        }
    }

    /**
     * 判断是否应该卖出
     *
     * @param score 策略得分
     * @return 是否应该卖出
     */
    public boolean shouldSell(StrategyScore score) {
        // 移动止损触发，立即卖出
        if (score.isTrailingStopTriggered()) {
            return true;
        }
        
        // 综合得分超过动态阈值
        return score.getTotalScore() >= score.getDynamicThreshold();
    }
}
