package com.stock.strategyAnalysis.engine;

import com.stock.strategyAnalysis.config.StrategyConfigService;
import com.stock.strategyAnalysis.domain.dto.StockRankingDto;
import com.stock.strategyAnalysis.domain.entity.Signal;
import com.stock.strategyAnalysis.domain.entity.StrategyConfig;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalTime;
import java.util.List;

/**
 * 决策引擎
 * 综合选股结果和策略配置生成交易决策
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DecisionEngine {

    private final StockSelector stockSelector;
    private final StrategyConfigService configService;

    /**
     * 生成交易决策
     */
    public DecisionResult makeDecision() {
        log.info("开始生成交易决策...");

        LocalTime now = LocalTime.now();

        // 判断是否在交易时间
        if (!isTradingTime(now)) {
            log.info("当前非交易时间");
            return DecisionResult.builder()
                    .signal(Signal.HOLD)
                    .reason("非交易时间")
                    .build();
        }

        try {
            // 获取选股结果
            List<StockRankingDto> rankings = stockSelector.getAllRankings();

            if (rankings.isEmpty()) {
                return DecisionResult.builder()
                        .signal(Signal.HOLD)
                        .reason("无可推荐股票")
                        .build();
            }

            // 选择得分最高的股票
            StockRankingDto top = rankings.get(0);
            StrategyConfig config = configService.getCurrentConfig();

            if (top.getTotalScore() >= config.getMinScore()) {
                return DecisionResult.builder()
                        .signal(Signal.BUY)
                        .stockCode(top.getStockCode())
                        .confidence(top.getTotalScore())
                        .reason(top.getReason())
                        .build();
            } else {
                return DecisionResult.builder()
                        .signal(Signal.HOLD)
                        .reason(String.format("综合得分 %.2f 低于阈值 %.2f, 建议观望",
                                top.getTotalScore(), config.getMinScore()))
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

    /**
     * 判断是否在交易时间
     */
    private boolean isTradingTime(LocalTime time) {
        LocalTime morningStart = LocalTime.of(9, 30);
        LocalTime morningEnd = LocalTime.of(11, 30);
        LocalTime afternoonStart = LocalTime.of(13, 0);
        LocalTime afternoonEnd = LocalTime.of(15, 0);

        return (time.isAfter(morningStart) && time.isBefore(morningEnd)) ||
               (time.isAfter(afternoonStart) && time.isBefore(afternoonEnd));
    }

    /**
     * 决策结果
     */
    @Data
    @lombok.Builder
    public static class DecisionResult {
        private Signal signal;
        private String stockCode;
        private double confidence;
        private String reason;
    }
}
