// AI_GENERATE_START --
package com.stock.strategyAnalysis.domain.entity;

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
 * 策略配置实体（MySQL 持久化）。
 * 保存选股、指标权重和收益阈值等运行参数。
 *
 * @author mwangli
 * @since 2026-09-25
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

    @Column(name = "config_id", length = 64)
    private String configId;

    @Column(length = 32)
    private String version;

    @Enumerated(EnumType.STRING)
    @Column(length = 32)
    private StrategyMode mode;

    @Column(name = "lstm_weight", nullable = false)
    private double lstmWeight;

    @Column(name = "sentiment_weight", nullable = false)
    private double sentimentWeight;

    @Column(name = "top_n", nullable = false)
    private int topN;

    @Column(name = "min_score", nullable = false)
    private double minScore;

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

    @Column(name = "high_return_threshold", nullable = false)
    private int highReturnThreshold;

    @Column(name = "normal_return_threshold", nullable = false)
    private int normalReturnThreshold;

    @Column(name = "low_return_threshold", nullable = false)
    private int lowReturnThreshold;

    @Column(name = "loss_return_threshold", nullable = false)
    private int lossReturnThreshold;

    @Convert(converter = IndicatorEnabledConverter.class)
    @Column(name = "indicator_enabled", columnDefinition = "TEXT")
    private Map<String, Boolean> indicatorEnabled;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "update_time")
    private LocalDateTime updateTime;

    @Column(name = "create_time")
    private LocalDateTime createTime;

    /**
     * 创建默认策略配置。
     *
     * @return 默认策略配置
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
                .enabled(true)
                .updateTime(LocalDateTime.now())
                .createTime(LocalDateTime.now())
                .build();
    }
}
// AI_GENERATE_END --
