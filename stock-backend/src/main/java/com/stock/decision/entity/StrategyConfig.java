// AI_GENERATE_START-------------
package com.stock.decision.entity;

import com.stock.decision.converter.BooleanMapJsonConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

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
@Table(name = "strategy_config", uniqueConstraints = {
        @UniqueConstraint(name = "uk_strategy_config_version", columnNames = {"config_id", "version"})
})
public class StrategyConfig implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String configId;

    private String version;

    @Enumerated(EnumType.STRING)
    private StrategyMode mode;

    private double lstmWeight;

    private double sentimentWeight;

    @Column(name = "top_n")
    private int topN;

    private double minScore;

    /**
     * 候选股票允许参与选股的最小上市年限，单位为年；0 表示不启用该门槛。
     */
    private double minimumListingYears;

    private double trailingStopWeight;

    private double trailingStopTolerance;

    private double rsiWeight;

    private double rsiOverboughtThreshold;

    private double volumeWeight;

    private double volumeShrinkThreshold;

    private double bollingerWeight;

    private double bollingerBreakoutThreshold;

    private int highReturnThreshold;

    private int normalReturnThreshold;

    private int lowReturnThreshold;

    private int lossReturnThreshold;

    @Lob
    @Column(columnDefinition = "LONGTEXT")
    @Convert(converter = BooleanMapJsonConverter.class)
    private Map<String, Boolean> indicatorEnabled;

    private boolean enabled;

    private LocalDateTime updateTime;

    private LocalDateTime createTime;

    /** 初始化策略配置时间。 */
    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createTime = createTime == null ? now : createTime;
        updateTime = now;
    }

    /** 刷新策略配置更新时间。 */
    @PreUpdate
    protected void onUpdate() {
        updateTime = LocalDateTime.now();
    }

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
                .minimumListingYears(0.5)
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
// AI_GENERATE_END-------------
