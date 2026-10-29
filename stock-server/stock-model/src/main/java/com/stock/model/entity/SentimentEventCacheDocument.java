// AI_GENERATE_START-
package com.stock.model.entity;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 规范新闻事件的版本化情感推理缓存。
 * 用于历史快照回填断点续跑，模型版本或内容摘要变化时自动形成新缓存键。
 *
 * @author mwangli
 * @since 2026-10-29
 */
@Data
@Document(collection = "sentiment_event_cache")
@CompoundIndex(name = "idx_sentiment_event_model_time",
        def = "{'sentimentModelVersion': 1, 'updatedAt': -1}")
public class SentimentEventCacheDocument {

    /** 模型版本与事件内容身份组成的唯一缓存键。 */
    @Id
    private String id;

    /** 实际执行推理的情感模型版本。 */
    private String sentimentModelVersion;

    /** 优先使用内容哈希的事件身份。 */
    private String eventIdentity;

    /** 情感分类标签。 */
    private String label;

    /** 原始情感得分，范围 -1 至 1。 */
    private double score;

    /** 原始分类置信度，范围 0 至 1。 */
    private double confidence;

    /** 温度缩放后的校准置信度，范围 0 至 1。 */
    private double calibratedConfidence;

    /** 归一化信息熵，范围 0 至 1。 */
    private double entropy;

    /** 最高与次高类别概率差，范围 0 至 1。 */
    private double probabilityMargin;

    /** 是否因置信度不足而弃权。 */
    private boolean abstained;

    /** 本次输入的文本分块数量，范围 1 至 3。 */
    private int chunkCount;

    /** 实际模型输入文本覆盖率，范围 0 至 1。 */
    private double textCoverage;

    /** 归一化情感得分，范围 0 至 100。 */
    private double normalizedScore;

    /** 各情感分类标签的概率。 */
    private Map<String, Double> probabilities;

    /** 缓存最近写入时间。 */
    private LocalDateTime updatedAt;
}
// AI_GENERATE_END-
