package com.stock.strategyAnalysis.engine;

import com.stock.strategyAnalysis.config.StrategyConfigService;
import com.stock.strategyAnalysis.domain.dto.BacktestResultDto;
import com.stock.strategyAnalysis.domain.entity.StrategyConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

/**
 * 策略优化服务
 * 整合回测和参数优化功能
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StrategyOptimizer {

    private final BacktestEngine backtestEngine;
    private final ParameterOptimizer parameterOptimizer;
    private final StrategyConfigService configService;

    /**
     * 执行回测
     */
    public BacktestResultDto runBacktest(LocalDate startDate, LocalDate endDate) {
        StrategyConfig config = configService.getCurrentConfig();
        return backtestEngine.runBacktest(config, startDate, endDate);
    }

    /**
     * 执行参数优化
     */
    public StrategyConfig optimizeParameters(LocalDate startDate, LocalDate endDate) {
        log.info("开始参数优化");
        StrategyConfig currentConfig = configService.getCurrentConfig();
        return parameterOptimizer.gridSearchOptimize(currentConfig, startDate, endDate);
    }

    /**
     * 对比当前配置与优化后配置
     */
    public List<BacktestResultDto> compareOptimization(LocalDate startDate, LocalDate endDate) {
        StrategyConfig currentConfig = configService.getCurrentConfig();
        StrategyConfig optimizedConfig = optimizeParameters(startDate, endDate);

        return backtestEngine.compareConfigs(List.of(currentConfig, optimizedConfig), startDate, endDate);
    }

    /**
     * 应用优化结果
     */
    public void applyOptimization(StrategyConfig optimizedConfig) {
        configService.updateConfig(optimizedConfig);
        log.info("已应用优化后的策略配置");
    }
}
