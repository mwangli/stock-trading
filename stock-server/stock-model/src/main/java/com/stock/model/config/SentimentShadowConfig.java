// AI_GENERATE_START ---------
package com.stock.model.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Mengzi 情感模型影子运行与受控路由配置。
 *
 * @author mwangli
 * @since 2026-10-24
 */
@Data
@Component
@ConfigurationProperties(prefix = "models.sentiment-shadow")
public class SentimentShadowConfig {

    /** 是否允许执行 Mengzi 影子推理。 */
    private boolean enabled;

    /** Mengzi 模型逻辑名称。 */
    private String modelName = "sentiment-mengzi";

    /** Mengzi 不可变制品版本。 */
    private String modelVersion = "mengzi-bert-base-fin-3e525b4d-djl";

    /** Mengzi 本地制品根目录。 */
    private String modelPath = "./model-files/sentiment";

    /** 最大输入序列长度。 */
    private int maxSequenceLength = 256;

    /** 金标验证集拟合得到的温度缩放参数，未校准时固定为 1。 */
    private double calibrationTemperature = 1D;

    /** 单次影子任务最大处理事件数。 */
    private int batchSize = 200;

    /** 影子任务回看自然日数。 */
    private int lookbackDays = 7;

    /** 允许自动激活要求的最少影子交易日。 */
    private int minimumShadowTradingDays = 20;

    /** 允许自动激活要求的最少已评估样本数。 */
    private int minimumEvaluatedSamples = 500;

    /** 允许自动激活要求的最低 Rank IC。 */
    private double minimumRankIc = 0.02D;

    /** 允许自动激活要求的最低方向准确率。 */
    private double minimumDirectionAccuracy = 0.52D;

    /** 允许自动激活的最高推理失败率。 */
    private double maximumFailureRate = 0.01D;

    /** 候选模型相对 FinBERT 的最低扣成本 Top 10% 收益增量。 */
    private double minimumTopDecileNetReturnImprovement;

    /** 候选情感分数最低标准差，低于该值视为输出塌缩。 */
    private double minimumOutputStd = 0.02D;

    /** 激活后自动回滚观察窗口交易日数。 */
    private int rollbackWindowTradingDays = 10;

    /** 模型输出标签顺序。 */
    private String[] labels = {"neutral", "positive", "negative"};
}
// AI_GENERATE_END ---------
