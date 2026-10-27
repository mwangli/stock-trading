// AI_GENERATE_START ---------
package com.stock.model.config;

import jakarta.annotation.PostConstruct;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Ranking LSTM 与 MASTER 四任务连续权重融合配置。
 *
 * @author mwangli
 * @since 2026-10-24
 */
@Data
@Component
@ConfigurationProperties(prefix = "models.temporal-ensemble")
public class TemporalEnsembleConfig {

    /** 是否执行 MASTER 影子预测。 */
    private boolean masterShadowEnabled;

    /** 是否允许生产候选应用四任务融合输出。 */
    private boolean productionBlendEnabled;

    /** 两个时序模型是否已使用同一新闻事件特征契约完成重训。 */
    private boolean newsFeaturesEmbedded;

    /** 允许关闭独立新闻加权项的新闻事件特征版本。 */
    private String newsFeatureVersion;

    /** 动态权重评估窗口交易日数。 */
    private int rollingWindowTradingDays = 20;

    /** 启用生产融合前至少需要的已评估交易日数。 */
    private int minimumEvaluatedTradingDays = 10;

    /** 启用生产融合前至少需要的已评估股票样本数。 */
    private int minimumEvaluatedSamples = 500;

    /** 最低输出标准差，低于该值视为输出塌缩。 */
    private double minimumOutputStd = 0.01D;

    /** MASTER 权重回放搜索步长。 */
    private double weightSearchStep = 0.05D;

    /** 单次状态刷新允许的最大权重变化。 */
    private double maximumWeightChange = 0.10D;

    /** 最优组合相对当前组合触发调权的最低质量分提升。 */
    private double minimumCombinationImprovement = 0.05D;

    /** 同一交易日双模型股票输出的最低覆盖率。 */
    private double minimumCoverageRate = 0.95D;

    @PostConstruct
    void validate() {
        requirePositive("滚动评估窗口交易日数", rollingWindowTradingDays);
        requirePositive("最低评估交易日数", minimumEvaluatedTradingDays);
        requirePositive("最低评估股票样本数", minimumEvaluatedSamples);
        if (minimumEvaluatedTradingDays > rollingWindowTradingDays) {
            throw new IllegalStateException("最低评估交易日数不能大于滚动评估窗口交易日数");
        }
        if (!Double.isFinite(minimumOutputStd) || minimumOutputStd <= 0D) {
            throw new IllegalStateException("最低输出标准差必须大于 0");
        }
        requireProbability("权重搜索步长", weightSearchStep);
        long steps = Math.round(1D / weightSearchStep);
        if (steps <= 0L || Math.abs(steps * weightSearchStep - 1D) > 1.0E-9D) {
            throw new IllegalStateException("权重搜索步长必须能够从 0 完整覆盖到 1");
        }
        requireProbability("单次最大权重变化", maximumWeightChange);
        if (maximumWeightChange + 1.0E-9D < weightSearchStep) {
            throw new IllegalStateException("单次最大权重变化不能小于权重搜索步长");
        }
        if (!Double.isFinite(minimumCombinationImprovement)
                || minimumCombinationImprovement < 0D) {
            throw new IllegalStateException("最小组合质量提升不能小于 0");
        }
        requireProbability("最低覆盖率", minimumCoverageRate);
    }

    private void requirePositive(String name, int value) {
        if (value <= 0) {
            throw new IllegalStateException(name + "必须大于 0");
        }
    }

    private void requireProbability(String name, double value) {
        if (!Double.isFinite(value) || value <= 0D || value > 1D) {
            throw new IllegalStateException(name + "必须位于 (0,1] 范围");
        }
    }
}
// AI_GENERATE_END ---------
