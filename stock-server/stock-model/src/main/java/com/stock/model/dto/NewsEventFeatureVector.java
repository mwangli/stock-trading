// AI_GENERATE_START -----
package com.stock.model.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.List;

/**
 * 单只股票在指定交易时点的新闻事件模型特征。
 *
 * @author mwangli
 * @since 2026-10-24
 */
@Data
@Builder
public class NewsEventFeatureVector {

    /** 股票代码。 */
    private String stockCode;

    /** 特征归属交易日。 */
    private LocalDate featureTradeDate;

    /** 点时信息截止时间，事件发布时间不得晚于该时间。 */
    private LocalDateTime cutoffTime;

    /** 聚合影响方向，取值为 -1、0 或 1。 */
    private double impactDirection;

    /** 聚合影响幅度，范围 0 到 1。 */
    private double impactMagnitude;

    /** 事件平均重要性，范围 0 到 1。 */
    private double materiality;

    /** 事件平均新颖度，范围 0 到 1。 */
    private double novelty;

    /** 情感模型聚合置信度，范围 0 到 1。 */
    private double confidence;

    /** 最近有效事件年龄，单位为小时。 */
    private double eventAgeHours;

    /** 是否存在独立风险门禁事件。 */
    private boolean riskFlag;

    /** 结构化事件类型计数。 */
    private Map<String, Integer> eventTypeCounts;

    /** 是否没有可用于方向预测的有效事件。 */
    private boolean noEffectiveEvent;

    /** 缺失掩码，1 表示方向事件特征缺失，0 表示完整。 */
    private int missingMask;

    /** 事件事实采集治理版本。 */
    private String pipelineVersion;

    /** 进入快照计算的规范事件数量。 */
    private int inputEventCount;

    /** 进入快照计算的最晚事件发布时间，可为空。 */
    private LocalDateTime latestInputPublishTime;

    /** 生成方向和置信度特征时使用的情感模型版本。 */
    private String sentimentModelVersion;

    /**
     * `temporal-news-v1` 固定 25 维向量：三类概率、情感分数、校准置信度、熵、概率间隔、
     * 弃权率、事件覆盖、影响幅度、重要性、新颖度、个股相关度、时间衰减、事件年龄、
     * 事件数量、来源质量、七类事件占比和缺失状态。
     */
    private List<Double> temporalFeatures;
}
// AI_GENERATE_END -----
