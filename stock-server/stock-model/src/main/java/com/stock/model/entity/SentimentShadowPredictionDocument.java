// AI_GENERATE_START -----
package com.stock.model.entity;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * Mengzi 对规范新闻事件事实的影子推理和后验评估记录。
 *
 * @author mwangli
 * @since 2026-10-24
 */
@Data
@Document(collection = "sentiment_shadow_predictions")
@CompoundIndex(name = "uk_sentiment_shadow_fact_model",
        def = "{'eventFactId': 1, 'candidateModelVersion': 1}", unique = true)
public class SentimentShadowPredictionDocument {

    /** MongoDB 文档 ID。 */
    @Id
    private String id;

    /** 规范事件事实 ID。 */
    private String eventFactId;

    /** 规范事件标识。 */
    private String canonicalEventId;

    /** 股票代码。 */
    private String stockCode;

    /** 事件发布时间。 */
    private LocalDateTime publishTime;

    /** 采集治理管线版本。 */
    private String pipelineVersion;

    /** 生产情感模型版本。 */
    private String productionModelVersion;

    /** FinBERT 基线预测标签。 */
    private String productionLabel;

    /** FinBERT 基线连续情感分数，范围 -1 到 1。 */
    private Double productionImpactScore;

    /** FinBERT 基线置信度，范围 0 到 1。 */
    private Double productionConfidence;

    /** FinBERT 基线是否因不确定性而弃权。 */
    private Boolean productionAbstained;

    /** 候选情感模型名称。 */
    private String candidateModelName;

    /** 候选情感模型版本。 */
    private String candidateModelVersion;

    /** 候选模型预测标签。 */
    private String label;

    /** 影响方向：BULLISH、BEARISH 或 NEUTRAL。 */
    private String impactDirection;

    /** 连续情感分数，范围 -1 到 1。 */
    private Double impactScore;

    /** 影响幅度，范围 0 到 1。 */
    private Double impactMagnitude;

    /** 模型置信度，范围 0 到 1。 */
    private Double confidence;

    /** 校准后的候选模型置信度，范围 0 到 1。 */
    private Double calibratedConfidence;

    /** 候选模型归一化信息熵，范围 0 到 1。 */
    private Double entropy;

    /** 候选模型最高与次高概率差，范围 0 到 1。 */
    private Double probabilityMargin;

    /** 候选模型是否因不确定性而弃权。 */
    private Boolean abstained;

    /** 事件类型。 */
    private String eventType;

    /** 分类概率。 */
    private Map<String, Double> probabilities;

    /** 评估使用的目标交易日。 */
    private LocalDate evaluationTradeDate;

    /** 事件后的实际收益。 */
    private Double actualReturn;

    /** 方向是否命中。 */
    private Boolean directionCorrect;

    /** FinBERT 基线方向是否命中。 */
    private Boolean productionDirectionCorrect;

    /** 评估状态：PENDING、EVALUATED 或 NO_MARKET_DATA。 */
    private String evaluationStatus;

    /** 影子推理失败原因，不包含原始文本或敏感配置。 */
    private String failureReason;

    /** 推理时间。 */
    private LocalDateTime predictedAt;

    /** 评估时间。 */
    private LocalDateTime evaluatedAt;
}
// AI_GENERATE_END -----
