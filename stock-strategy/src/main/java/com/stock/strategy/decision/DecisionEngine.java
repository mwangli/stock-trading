package com.stock.strategy.decision;

import com.stock.common.enums.Signal;
import com.stock.strategy.selector.StockSelector;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class DecisionEngine {

    private final StockSelector stockSelector;

    public DecisionResult makeDecision() {
        log.info("开始生成交易决策...");
        
        LocalTime now = LocalTime.now();
        
        // 判断是否在交易时间
        boolean isTradingTime = isTradingTime(now);
        
        if (!isTradingTime) {
            log.info("当前非交易时间");
            return DecisionResult.builder()
                    .signal(Signal.HOLD)
                    .reason("非交易时间")
                    .build();
        }
        
        try {
            // 获取推荐股票
            List<StockSelector.StockRecommendation> recommendations = 
                    stockSelector.select(5);
            
            if (recommendations.isEmpty()) {
                return DecisionResult.builder()
                        .signal(Signal.HOLD)
                        .reason("无可推荐股票")
                        .build();
            }
            
            // 选择得分最高的股票
            StockSelector.StockRecommendation top = recommendations.get(0);
            
            if (top.getScore() > 0.3) {
                return DecisionResult.builder()
                        .signal(Signal.BUY)
                        .stockCode(top.getStockCode())
                        .confidence(Math.min(top.getScore(), 1.0))
                        .reason(String.format("综合得分 %.2f, 建议买入", top.getScore()))
                        .recommendations(recommendations)
                        .build();
            } else if (top.getScore() < -0.3) {
                return DecisionResult.builder()
                        .signal(Signal.SELL)
                        .stockCode(top.getStockCode())
                        .confidence(Math.min(-top.getScore(), 1.0))
                        .reason(String.format("综合得分 %.2f, 建议卖出", top.getScore()))
                        .build();
            } else {
                return DecisionResult.builder()
                        .signal(Signal.HOLD)
                        .reason(String.format("综合得分 %.2f, 建议观望", top.getScore()))
                        .recommendations(recommendations)
                        .build();
            }
            
        } catch (Exception e) {
            log.error("生成决策失败", e);
            return DecisionResult.builder()
                    .signal(Signal.HOLD)
                    .reason("系统异常")
                    .build();
        }
    }

    private boolean isTradingTime(LocalTime time) {
        LocalTime morningStart = LocalTime.of(9, 30);
        LocalTime morningEnd = LocalTime.of(11, 30);
        LocalTime afternoonStart = LocalTime.of(13, 0);
        LocalTime afternoonEnd = LocalTime.of(15, 0);
        
        return (time.isAfter(morningStart) && time.isBefore(morningEnd)) ||
               (time.isAfter(afternoonStart) && time.isBefore(afternoonEnd));
    }

    @Data
    @lombok.Builder
    public static class DecisionResult {
        private Signal signal;
        private String stockCode;
        private double confidence;
        private String reason;
        private List<StockSelector.StockRecommendation> recommendations;
    }
}
