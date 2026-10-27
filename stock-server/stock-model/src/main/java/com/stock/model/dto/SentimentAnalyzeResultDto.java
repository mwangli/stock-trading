// AI_GENERATE_START------
package com.stock.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 情感分析结果 DTO
 * <p>
 * 封装情感标签、得分、置信度以及概率分布等信息，
 * 作为对外 API 的统一返回结构，避免直接暴露 Map。
 *
 * @author mwangli
 * @since 2026-10-18
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SentimentAnalyzeResultDto {

    /**
     * 是否调用成功
     */
    private boolean success;

    /**
     * 提示信息（错误信息或说明）
     */
    private String message;

    /**
     * 情感标签：positive / neutral / negative
     */
    private String label;

    /**
     * 原始情感分数（正负值）
     */
    private Double score;

    /**
     * 归一化情感得分（0-100）
     */
    private Double normalizedScore;

    /**
     * 置信度（预测类别的概率）
     */
    private Double confidence;

    /** 温度缩放后的校准置信度，范围 0 到 1。 */
    private Double calibratedConfidence;

    /** 归一化信息熵，范围 0 到 1。 */
    private Double entropy;

    /** 最高和次高类别概率差，范围 0 到 1。 */
    private Double probabilityMargin;

    /** 是否因不确定性不足而弃权。 */
    private Boolean abstained;

    /** 本次推理使用的文本分块数量。 */
    private Integer chunkCount;

    /** 实际输入文本覆盖率，范围 0 到 1。 */
    private Double textCoverage;

    /**
     * 各情感类别的概率分布
     */
    private Map<String, Double> probabilities;

    /**
     * 原始文本内容
     */
    private String text;

    /**
     * 是否实际加载了预训练模型
     */
    private Boolean modelLoaded;

    /**
     * 关联的股票代码（按股票分析时使用，可为空）
     */
    private String stockCode;

    /** 事件类型，与 StockNews.eventType 使用同一枚举值。 */
    private String eventType;

    /** 影响方向：BULLISH、BEARISH 或 NEUTRAL。 */
    private String impactDirection;

    /** 影响强度，范围为 0 到 1。 */
    private Double impactMagnitude;

    /** 新闻影响分数，范围为 -1 到 1。 */
    private Double newsImpactScore;

    /** 有效窗口，单位为小时。 */
    private Integer effectiveHorizonHours;

    /** 是否达到进入选股排序的最低门槛。 */
    private Boolean actionable;

    /** 策略动作：ENHANCE、REDUCE、NO_ACTION 或 RISK_BLOCK。 */
    private String strategyAction;

    /** 是否触发强负面风险阻断。 */
    private Boolean strongNegative;
}
// AI_GENERATE_END------
