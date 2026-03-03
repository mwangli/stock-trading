package com.stock.strategyAnalysis.optimizer;

import com.stock.strategyAnalysis.entity.StrategyConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 参数优化器
 * 自动寻找最优策略参数
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ParameterOptimizer {

    private final BacktestEngine backtestEngine;

    /**
     * 网格搜索优化
     */
    public StrategyConfig gridSearchOptimize(StrategyConfig baseConfig, LocalDate startDate, LocalDate endDate) {
        log.info("开始网格搜索优化");
        
        double bestScore = Double.NEGATIVE_INFINITY;
        StrategyConfig bestConfig = baseConfig;
        
        // 遍历止损容忍度参数
        for (double tolerance = 0.01; tolerance <= 0.05; tolerance += 0.01) {
            StrategyConfig testConfig = copyConfig(baseConfig);
            testConfig.setTrailingStopTolerance(tolerance);
            
            var result = backtestEngine.runBacktest(testConfig, startDate, endDate);
            double score = result.getTotalReturn() - result.getMaxDrawdown();
            
            if (score > bestScore) {
                bestScore = score;
                bestConfig = testConfig;
            }
        }
        
        log.info("网格搜索完成, 最优止损容忍度: {}", bestConfig.getTrailingStopTolerance());
        return bestConfig;
    }

    /**
     * 复制配置
     */
    private StrategyConfig copyConfig(StrategyConfig source) {
        return StrategyConfig.builder()
                .configId(java.util.UUID.randomUUID().toString())
                .mode(source.getMode())
                .lstmWeight(source.getLstmWeight())
                .sentimentWeight(source.getSentimentWeight())
                .topN(source.getTopN())
                .minScore(source.getMinScore())
                .trailingStopWeight(source.getTrailingStopWeight())
                .trailingStopTolerance(source.getTrailingStopTolerance())
                .rsiWeight(source.getRsiWeight())
                .rsiOverboughtThreshold(source.getRsiOverboughtThreshold())
                .volumeWeight(source.getVolumeWeight())
                .volumeShrinkThreshold(source.getVolumeShrinkThreshold())
                .bollingerWeight(source.getBollingerWeight())
                .bollingerBreakoutThreshold(source.getBollingerBreakoutThreshold())
                .highReturnThreshold(source.getHighReturnThreshold())
                .normalReturnThreshold(source.getNormalReturnThreshold())
                .lowReturnThreshold(source.getLowReturnThreshold())
                .lossReturnThreshold(source.getLossReturnThreshold())
                .enabled(true)
                .build();
    }
}