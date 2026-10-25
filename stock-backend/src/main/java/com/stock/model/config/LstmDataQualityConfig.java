// AI_GENERATE_START-------------
package com.stock.model.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;

/**
 * LSTM 数据质量配置
 * <p>
 * 用于配置在外部数据源中历史数据永远不足、无法满足训练要求的股票代码列表，
 * 这些股票会在启动训练时直接跳过，避免陷入“清理-重拉-再失败”的死循环。
 * </p>
 *
 * @author mwangli
 * @since 2026-10-11
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.lstm.data-quality")
public class LstmDataQualityConfig {

    /**
     * 永久跳过训练的股票代码列表
     * <p>
     * 典型场景：新上市或长期停牌股票，在券商接口中只有几十条历史数据，
     * 无法满足 LSTM 所需的最小样本数量。
     * </p>
     */
    private Set<String> skipTrainingCodes = new HashSet<>();

    /**
     * 单次共享训练要求的最小有效股票数量。
     */
    private int minimumTrainingStockCount = 100;

    /**
     * 单次训练要求的最小训练样本数量。
     */
    private int minimumTrainSamples = 1000;

    /**
     * 单次训练要求的最小验证样本数量。
     */
    private int minimumValidationSamples = 200;

    /** 单次训练要求的最小独立测试样本数量。 */
    private int minimumTestSamples = 200;

    /** Walk-Forward 评估要求的最小有效窗口数量。 */
    private int minimumWalkForwardFoldCount = 3;

    /** 关键数据源按股票或行业标识计算的最低覆盖率。 */
    private double minimumSourceCoverageRate = 0.95D;

    /**
     * 获取关键数据源最低覆盖率。
     *
     * @return 最低覆盖率，取值范围为大于 0 且小于等于 1
     */
    public double getMinimumSourceCoverageRate() {
        return minimumSourceCoverageRate;
    }

    /**
     * 候选模型允许的最大验证损失。
     */
    private double maximumValidationLoss = 1.0D;

    /** 候选模型要求的最低扣成本收益，实际判断必须严格大于该值。 */
    private double minimumNetReturn = 0D;

    /** 候选模型最大允许回撤。 */
    private double maximumDrawdown = 0.20D;

    /** 没有生产模型时允许自动晋级的最低综合质量分。 */
    private double minimumInitialQualityScore = 0.20D;

    /** 候选模型相对生产模型自动晋级所需的最低综合质量分优势。 */
    private double minimumQualityScoreAdvantage = 0.05D;

    /**
     * 启动训练要求的最小 JVM 可用内存，单位 MB。
     */
    private long minimumAvailableMemoryMb = 256L;
}
// AI_GENERATE_END-------------
