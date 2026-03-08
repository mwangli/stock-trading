package com.stock.strategyAnalysis.entity;

import com.stock.strategyAnalysis.converter.IndicatorEnabledConverter;
import com.stock.strategyAnalysis.enums.StrategyMode;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 策略配置实体（MySQL 持久化）
 * 用于选股、T+1 与日内卖出指标的参数存储，交易时由 StrategyConfigService 提供
 *
 * @author AI Assistant
 * @since 1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "strategy_config")
public class StrategyConfig implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 配置业务 ID（如 default 或 UUID）
     */
    @Column(name = "config_id", length = 64)
    private String configId;

    @Column(length = 32)
    private String version;

    @Enumerated(EnumType.STRING)
    @Column(length = 32)
    private StrategyMode mode;

    // ==================== 选股参数 ====================

    @Column(name = "lstm_weight", nullable = false)
    private double lstmWeight;

    @Column(name = "sentiment_weight", nullable = false)
    private double sentimentWeight;

    @Column(name = "top_n", nullable = false)
    private int topN;

    @Column(name = "min_score", nullable = false)
    private double minScore;

    // ==================== T+1 指标配置 ====================

    @Column(name = "trailing_stop_weight", nullable = false)
    private double trailingStopWeight;

    @Column(name = "trailing_stop_tolerance", nullable = false)
    private double trailingStopTolerance;

    @Column(name = "rsi_weight", nullable = false)
    private double rsiWeight;

    @Column(name = "rsi_overbought_threshold", nullable = false)
    private double rsiOverboughtThreshold;

    @Column(name = "volume_weight", nullable = false)
    private double volumeWeight;

    @Column(name = "volume_shrink_threshold", nullable = false)
    private double volumeShrinkThreshold;

    @Column(name = "bollinger_weight", nullable = false)
    private double bollingerWeight;

    @Column(name = "bollinger_breakout_threshold", nullable = false)
    private double bollingerBreakoutThreshold;

    // ==================== 动态阈值配置 ====================

    @Column(name = "high_return_threshold", nullable = false)
    private int highReturnThreshold;

    @Column(name = "normal_return_threshold", nullable = false)
    private int normalReturnThreshold;

    @Column(name = "low_return_threshold", nullable = false)
    private int lowReturnThreshold;

    @Column(name = "loss_return_threshold", nullable = false)
    private int lossReturnThreshold;

    // ==================== 指标开关（JSON 存储） ====================

    @Convert(converter = IndicatorEnabledConverter.class)
    @Column(name = "indicator_enabled", columnDefinition = "TEXT")
    private Map<String, Boolean> indicatorEnabled;

    // ==================== 熔断配置 ====================

    @Column(name = "consecutive_failure_threshold", nullable = false)
    private int consecutiveFailureThreshold;

    @Column(name = "daily_failure_threshold", nullable = false)
    private int dailyFailureThreshold;

    @Column(name = "circuit_breaker_recovery_minutes", nullable = false)
    private int circuitBreakerRecoveryMinutes;

    // ==================== 状态信息 ====================

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "update_time")
    private LocalDateTime updateTime;

    @Column(name = "create_time")
    private LocalDateTime createTime;

    /**
     * 返回默认策略配置（无持久化配置时供接口返回）
     */
    public static StrategyConfig defaultConfig() {
        Map<String, Boolean> indicatorEnabled = new HashMap<>();
        indicatorEnabled.put("trailingStop", true);
        indicatorEnabled.put("rsi", true);
        indicatorEnabled.put("volume", true);
        indicatorEnabled.put("bollinger", true);
        return StrategyConfig.builder()
                .configId("default")
                .version("1")
                .mode(StrategyMode.BALANCED)
                .lstmWeight(0.5)
                .sentimentWeight(0.5)
                .topN(10)
                .minScore(0.5)
                .trailingStopWeight(0.25)
                .trailingStopTolerance(0.02)
                .rsiWeight(0.25)
                .rsiOverboughtThreshold(70.0)
                .volumeWeight(0.25)
                .volumeShrinkThreshold(0.5)
                .bollingerWeight(0.25)
                .bollingerBreakoutThreshold(1.0)
                .highReturnThreshold(3)
                .normalReturnThreshold(2)
                .lowReturnThreshold(1)
                .lossReturnThreshold(0)
                .indicatorEnabled(indicatorEnabled)
                .consecutiveFailureThreshold(3)
                .dailyFailureThreshold(5)
                .circuitBreakerRecoveryMinutes(30)
                .enabled(true)
                .updateTime(LocalDateTime.now())
                .createTime(LocalDateTime.now())
                .build();
    }
}
