package com.stock.strategyAnalysis.intraday;

import com.stock.strategyAnalysis.config.StrategyConfigService;
import com.stock.strategyAnalysis.entity.MinuteBar;
import com.stock.strategyAnalysis.entity.SellDecision;
import com.stock.strategyAnalysis.entity.StrategyConfig;
import com.stock.strategyAnalysis.entity.StrategyScore;
import com.stock.strategyAnalysis.enums.SellTriggerType;
import com.stock.strategyAnalysis.repository.DecisionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * T+1 卖出服务
 * 基于分钟级价格走势的实时卖出决策
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IntradaySellService {

    private static final LocalTime FORCE_SELL_TIME = LocalTime.of(14, 57);
    
    private final TrailingStopCalculator trailingStopCalculator;
    private final RsiCalculator rsiCalculator;
    private final VolumeCalculator volumeCalculator;
    private final BollingerCalculator bollingerCalculator;
    private final DecisionAggregator decisionAggregator;
    private final StrategyConfigService configService;
    private final DecisionRepository decisionRepository;
    private final RedisTemplate<String, Object> redisTemplate;

    /**
     * 检查卖出信号
     *
     * @param stockCode 股票代码
     * @param bar 当前分钟K线
     * @param historyBars 历史K线列表
     * @return 卖出决策
     */
    public SellDecision checkSellSignal(String stockCode, MinuteBar bar, List<MinuteBar> historyBars) {
        log.debug("检查股票 {} 卖出信号, 当前价 {}", stockCode, bar.getClosePrice());
        
        StrategyConfig config = configService.getCurrentConfig();
        
        // 更新止损线
        double dailyAmplitude = calculateDailyAmplitude(historyBars);
        trailingStopCalculator.updateStopLoss(stockCode, bar.getClosePrice(), dailyAmplitude, config);
        
        // 计算策略得分
        double openPrice = getOpenPrice(historyBars);
        StrategyScore score = decisionAggregator.calculateScore(
                stockCode, historyBars, bar.getClosePrice(), openPrice);
        
        // 判断是否应该卖出
        boolean shouldSell = decisionAggregator.shouldSell(score);
        
        // 确定触发类型
        SellTriggerType triggerType = determineTriggerType(score, shouldSell);
        
        SellDecision decision = SellDecision.builder()
                .decisionId(UUID.randomUUID().toString())
                .stockCode(stockCode)
                .shouldSell(shouldSell)
                .triggerType(triggerType)
                .score(score.getTotalScore())
                .currentPrice(bar.getClosePrice())
                .stopLossPrice(trailingStopCalculator.getStopLossPrice(stockCode))
                .reason(buildReason(score, shouldSell))
                .timestamp(LocalDateTime.now())
                .trailingStopTriggered(score.isTrailingStopTriggered())
                .rsiValue(score.getRsiValue())
                .volumeRatio(score.getVolumeRatio())
                .bollingerBreakout(score.isBollingerBreakout())
                .build();
        
        // 如果决定卖出，保存决策
        if (shouldSell) {
            decisionRepository.save(decision);
            log.info("股票 {} 生成卖出决策: 得分={}, 触发类型={}", 
                    stockCode, score.getTotalScore(), triggerType);
        }
        
        return decision;
    }

    /**
     * 批量检查所有持仓的卖出信号
     *
     * @param positions 持仓列表 Map<股票代码, 分钟K线列表>
     * @return 卖出决策列表
     */
    public List<SellDecision> checkAllPositions(Map<String, List<MinuteBar>> positions) {
        List<SellDecision> decisions = new ArrayList<>();
        
        for (Map.Entry<String, List<MinuteBar>> entry : positions.entrySet()) {
            String stockCode = entry.getKey();
            List<MinuteBar> bars = entry.getValue();
            
            if (bars != null && !bars.isEmpty()) {
                MinuteBar latestBar = bars.get(bars.size() - 1);
                SellDecision decision = checkSellSignal(stockCode, latestBar, bars);
                decisions.add(decision);
            }
        }
        
        return decisions;
    }

    /**
     * 尾盘强制卖出
     * 14:57 强制卖出所有持仓
     *
     * @param stockCodes 持仓股票代码列表
     * @return 卖出决策列表
     */
    public List<SellDecision> forceSellAtEnd(List<String> stockCodes) {
        log.info("执行尾盘强制卖出, 股票数量: {}", stockCodes.size());
        
        List<SellDecision> decisions = new ArrayList<>();
        
        for (String stockCode : stockCodes) {
            SellDecision decision = SellDecision.builder()
                    .decisionId(UUID.randomUUID().toString())
                    .stockCode(stockCode)
                    .shouldSell(true)
                    .triggerType(SellTriggerType.FORCE)
                    .score(100)
                    .reason("尾盘强制卖出 (14:57)")
                    .timestamp(LocalDateTime.now())
                    .build();
            
            decisions.add(decision);
            decisionRepository.save(decision);
        }
        
        return decisions;
    }

    /**
     * 强制卖出指定股票
     */
    public SellDecision forceSell(String stockCode, String reason) {
        SellDecision decision = SellDecision.builder()
                .decisionId(UUID.randomUUID().toString())
                .stockCode(stockCode)
                .shouldSell(true)
                .triggerType(SellTriggerType.FORCE)
                .score(100)
                .reason("强制卖出: " + reason)
                .timestamp(LocalDateTime.now())
                .build();
        
        decisionRepository.save(decision);
        log.info("股票 {} 强制卖出: {}", stockCode, reason);
        
        return decision;
    }

    /**
     * 重置股票的止损状态
     */
    public void resetStopLoss(String stockCode, double openPrice) {
        StrategyConfig config = configService.getCurrentConfig();
        trailingStopCalculator.reset(stockCode, openPrice, config);
    }

    /**
     * 获取策略得分
     */
    public StrategyScore getStrategyScore(String stockCode, List<MinuteBar> bars, double openPrice) {
        if (bars == null || bars.isEmpty()) {
            return null;
        }
        
        double currentPrice = bars.get(bars.size() - 1).getClosePrice();
        return decisionAggregator.calculateScore(stockCode, bars, currentPrice, openPrice);
    }

    /**
     * 判断是否到强制卖出时间
     */
    public boolean isForceSellTime() {
        LocalTime now = LocalTime.now();
        return now.isAfter(FORCE_SELL_TIME) || now.equals(FORCE_SELL_TIME);
    }

    /**
     * 确定触发类型
     */
    private SellTriggerType determineTriggerType(StrategyScore score, boolean shouldSell) {
        if (!shouldSell) {
            return null;
        }
        
        if (score.isTrailingStopTriggered()) {
            return SellTriggerType.TRAILING_STOP;
        }
        
        if (score.isBollingerBreakout()) {
            return SellTriggerType.BOLLINGER;
        }
        
        if (score.getRsiValue() > 75) {
            return SellTriggerType.RSI;
        }
        
        if (score.getVolumeRatio() < 0.8) {
            return SellTriggerType.VOLUME;
        }
        
        return SellTriggerType.SCORE;
    }

    /**
     * 构建决策原因
     */
    private String buildReason(StrategyScore score, boolean shouldSell) {
        if (!shouldSell) {
            return "未达到卖出条件";
        }
        
        StringBuilder reason = new StringBuilder();
        
        if (score.isTrailingStopTriggered()) {
            reason.append("移动止损触发; ");
        }
        if (score.getRsiValue() > 75) {
            reason.append(String.format("RSI超买(%.1f); ", score.getRsiValue()));
        }
        if (score.getVolumeRatio() < 0.8) {
            reason.append(String.format("成交量萎缩(%.2f); ", score.getVolumeRatio()));
        }
        if (score.isBollingerBreakout()) {
            reason.append("布林带突破; ");
        }
        
        reason.append(String.format("综合得分: %d, 阈值: %d", score.getTotalScore(), score.getDynamicThreshold()));
        
        return reason.toString();
    }

    /**
     * 计算日振幅
     */
    private double calculateDailyAmplitude(List<MinuteBar> bars) {
        if (bars == null || bars.isEmpty()) {
            return 0;
        }
        
        double highPrice = bars.stream().mapToDouble(MinuteBar::getHighPrice).max().orElse(0);
        double lowPrice = bars.stream().mapToDouble(MinuteBar::getLowPrice).min().orElse(0);
        
        if (lowPrice == 0) {
            return 0;
        }
        
        return (highPrice - lowPrice) / lowPrice;
    }

    /**
     * 获取开盘价
     */
    private double getOpenPrice(List<MinuteBar> bars) {
        if (bars == null || bars.isEmpty()) {
            return 0;
        }
        return bars.get(0).getOpenPrice();
    }
}
