package com.stock.modelService.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 情感分析模型评估配置
 */
@Data
@Component
@ConfigurationProperties(prefix = "models.sentiment.evaluation")
public class SentimentEvaluationConfig {

    /**
     * 准确率合格阈值 (%)
     *
     * 模型准确率达到此值视为合格，低于此值需要优化
     */
    private double accuracyThreshold = 70.0;

    /**
     * 准确率触发微调阈值 (%)
     *
     * 当准确率低于此值时，触发模型微调流程
     */
    private double accuracyTriggerThreshold = 70.0;

    /**
     * F1分数合格阈值 (%)
     *
     * 模型F1分数达到此值视为合格，用于衡量分类精确率和召回率的综合表现
     */
    private double f1Threshold = 60.0;

    /**
     * F1分数触发微调阈值 (%)
     *
     * 当F1分数低于此值时，触发模型微调流程
     */
    private double f1TriggerThreshold = 60.0;

    /**
     * 方向准确率合格阈值 (%)
     *
     * 衡量模型预测情感方向（涨跌）与实际结果一致的比例
     */
    private double directionAccuracyThreshold = 65.0;

    /**
     * ROC-AUC合格阈值
     *
     * 模型ROC-AUC指标达到此值视为合格，取值范围 0~1
     */
    private double rocAucThreshold = 0.70;

    /**
     * 夏普比率合格阈值
     *
     * 模型夏普比率达到此值视为合格，用于衡量策略风险调整后的收益表现
     */
    private double sharpeRatioThreshold = 0.5;

    /**
     * 夏普比率触发微调阈值
     *
     * 当夏普比率低于此值时，触发模型微调流程
     */
    private double sharpeRatioTriggerThreshold = 0.5;

    /**
     * 连续下降触发微调的周期数
     *
     * 当评估指标连续下降超过此周期数时，触发模型微调流程
     */
    private int consecutiveDeclineTrigger = 3;

    /**
     * 触发微调所需的最少标注样本数
     *
     * 标注样本达到此数量时才允许触发微调，确保微调数据充足
     */
    private int minSamplesForFineTune = 500;

    /**
     * 标注最小置信度
     *
     * 只有置信度高于此值的预测结果才会被用于标注
     */
    private double minConfidenceForLabel = 0.6;

    /**
     * 正收益标注阈值 (%)
     *
     * 当日收益率大于此值时，标注为利好样本
     */
    private double positiveReturnThreshold = 1.0;

    /**
     * 负收益标注阈值 (%)
     *
     * 当日收益率小于此值时，标注为利空样本
     */
    private double negativeReturnThreshold = -1.0;

    /**
     * 准确率回滚阈值 (%)
     *
     * 当准确率下降超过此值时，触发模型回滚
     */
    private double rollbackAccuracyDropThreshold = 5.0;

    /**
     * 夏普比率回滚阈值 (%)
     *
     * 当夏普比率下降超过此百分比时，触发模型回滚
     */
    private double rollbackSharpeDropThreshold = 20.0;

    /**
     * 无风险利率（用于夏普比率计算）
     *
     * 年化无风险利率，默认值对应国债收益率水平
     */
    private double riskFreeRate = 0.02;
}
