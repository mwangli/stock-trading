package com.stock.modelService.config;

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
 * @author AI Assistant
 * @since 1.0
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
}

