// AI_GENERATE_START----
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

    /**
     * 候选模型允许的最大验证损失。
     */
    private double maximumValidationLoss = 1.0D;

    /**
     * 启动训练要求的最小 JVM 可用内存，单位 MB。
     */
    private long minimumAvailableMemoryMb = 256L;
}
// AI_GENERATE_END----
