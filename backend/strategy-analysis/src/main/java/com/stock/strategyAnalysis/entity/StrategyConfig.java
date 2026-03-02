package com.stock.strategyAnalysis.entity;

import com.stock.strategyAnalysis.enums.StrategyMode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 策略配置实体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StrategyConfig {
    
    /**
     * 配置ID
     */
    private String configId;
    
    /**
     * 版本号
     */
    private String version;
    
    /**
     * 策略模式
     */
    private StrategyMode mode;
    
    // ==================== 选股参数 ====================
    
    /**
     * LSTM因子权重
     */
    private double lstmWeight;
    
    /**
     * 情感因子权重
     */
    private double sentimentWeight;
    
    /**
     * 选股数量
     */
    private int topN;
    
    /**
     * 最低综合得分阈值
     */
    private double minScore;
    
    // ==================== T+1指标配置 ====================
    
    /**
     * 移动止损权重
     */
    private double trailingStopWeight;
    
    /**
     * 移动止损回撤容忍度
     */
    private double trailingStopTolerance;
    
    /**
     * RSI权重
     */
    private double rsiWeight;
    
    /**
     * RSI超买阈值
     */
    private double rsiOverboughtThreshold;
    
    /**
     * 成交量权重
     */
    private double volumeWeight;
    
    /**
     * 成交量萎缩阈值
     */
    private double volumeShrinkThreshold;
    
    /**
     * 布林带权重
     */
    private double bollingerWeight;
    
    /**
     * 布林带突破阈值
     */
    private double bollingerBreakoutThreshold;
    
    // ==================== 动态阈值配置 ====================
    
    /**
     * 高收益卖出阈值 (>3%)
     */
    private int highReturnThreshold;
    
    /**
     * 正常卖出阈值 (1%-3%)
     */
    private int normalReturnThreshold;
    
    /**
     * 低收益卖出阈值 (0%-1%)
     */
    private int lowReturnThreshold;
    
    /**
     * 亏损卖出阈值 (<0%)
     */
    private int lossReturnThreshold;
    
    // ==================== 指标开关 ====================
    
    /**
     * 指标启用状态
     */
    private Map<String, Boolean> indicatorEnabled;
    
    // ==================== 熔断配置 ====================
    
    /**
     * 连续失败阈值
     */
    private int consecutiveFailureThreshold;
    
    /**
     * 当日失败阈值
     */
    private int dailyFailureThreshold;
    
    /**
     * 熔断恢复时间（分钟）
     */
    private int circuitBreakerRecoveryMinutes;
    
    // ==================== 状态信息 ====================
    
    /**
     * 是否启用
     */
    private boolean enabled;
    
    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
    
    /**
     * 创建时间
     */
    private LocalDateTime createTime;
    
    /**
     * 默认配置工厂方法
     */
    public static StrategyConfig defaultConfig() {
        return StrategyConfig.builder()
                .configId("default")
                .version("1.0.0")
                .mode(StrategyMode.BALANCED)
                .lstmWeight(0.6)
                .sentimentWeight(0.4)
                .topN(10)
                .minScore(0.4)
                .trailingStopWeight(0.4)
                .trailingStopTolerance(0.02)
                .rsiWeight(0.2)
                .rsiOverboughtThreshold(75.0)
                .volumeWeight(0.2)
                .volumeShrinkThreshold(0.8)
                .bollingerWeight(0.2)
                .bollingerBreakoutThreshold(1.005)
                .highReturnThreshold(50)
                .normalReturnThreshold(60)
                .lowReturnThreshold(70)
                .lossReturnThreshold(80)
                .consecutiveFailureThreshold(3)
                .dailyFailureThreshold(10)
                .circuitBreakerRecoveryMinutes(60)
                .enabled(true)
                .createTime(LocalDateTime.now())
                .updateTime(LocalDateTime.now())
                .build();
    }
}