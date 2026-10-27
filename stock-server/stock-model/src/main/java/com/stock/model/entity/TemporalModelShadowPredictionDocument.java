// AI_GENERATE_START ---
package com.stock.model.entity;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * MASTER 候选模型的单股影子预测记录。
 *
 * @author mwangli
 * @since 2026-10-24
 */
@Data
@Document(collection = "temporal_model_shadow_predictions")
@CompoundIndex(name = "uk_temporal_shadow_model_date_stock",
        def = "{'modelId': 1, 'baselineBatchId': 1, 'stockCode': 1}", unique = true)
public class TemporalModelShadowPredictionDocument {

    /** MongoDB 文档 ID。 */
    @Id
    private String id;

    /** MASTER 候选模型文档 ID。 */
    private String modelId;

    /** MASTER 候选模型版本。 */
    private String modelVersion;

    /** 对应的 Ranking LSTM 生产预测批次 ID。 */
    private String baselineBatchId;

    /** 股票代码。 */
    private String stockCode;

    /** 预测基准交易日。 */
    private LocalDate baseTradeDate;

    /** 预测目标交易日。 */
    private LocalDate targetTradeDate;

    /** MASTER 原始排序分数。 */
    private Double rankScore;

    /** MASTER 横截面 Rank Score，范围 0 到 1。 */
    private Double rankPercentile;

    /** 预期超额收益。 */
    private Double expectedExcessReturn;

    /** 上涨概率。 */
    private Double upProbability;

    /** 下行风险分位。 */
    private Double downsideQuantile;

    /** 实际收益。 */
    private Double actualReturn;

    /** 评估状态：PENDING、EVALUATED 或 NO_MARKET_DATA。 */
    private String evaluationStatus;

    /** 影子预测时间。 */
    private LocalDateTime predictedAt;

    /** 评估时间。 */
    private LocalDateTime evaluatedAt;
}
// AI_GENERATE_END ---
