// AI_GENERATE_START -------
package com.stock.model.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * MASTER 影子预测与 Ranking LSTM 动态融合配置。
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

    /** 是否允许生产候选应用融合 Rank Score。 */
    private boolean productionBlendEnabled;

    /** 两个时序模型是否已使用同一新闻事件特征契约完成重训。 */
    private boolean newsFeaturesEmbedded;

    /** 允许关闭独立新闻加权项的新闻事件特征版本。 */
    private String newsFeatureVersion;

    /** MASTER 自动晋级后的起始权重。 */
    private double initialMasterWeight = 0.10D;

    /** 动态权重评估窗口交易日数。 */
    private int rollingWindowTradingDays = 20;

    /** 启用生产融合前至少需要的已评估交易日数。 */
    private int minimumEvaluatedTradingDays = 10;

    /** 启用生产融合前至少需要的已评估股票样本数。 */
    private int minimumEvaluatedSamples = 500;

    /** 最低输出标准差，低于该值视为输出塌缩。 */
    private double minimumOutputStd = 0.01D;

    /** MASTER 相对质量优势转换为融合权重的倍率。 */
    private double weightAdvantageMultiplier = 2D;

    /** MASTER 在生产融合中的最大权重。 */
    private double maximumMasterWeight = 0.50D;
}
// AI_GENERATE_END -------
