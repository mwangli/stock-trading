package com.stock.modelService.domain.entity;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

/**
 * 情感模型评估结果文档
 * 用于在 MongoDB 中保存情感分析模型的评估指标数据，
 * 包含准确率、F1分数、夏普比率等关键性能指标，
 * 方便追踪模型效果和进行阈值告警。
 *
 * @author mwangli
 * @since 2026-03-29
 */
@Data
@Document(collection = "sentiment_evaluation_results")
public class SentimentEvaluation {

    /**
     * 文档主键，对应 MongoDB ObjectId
     */
    @Id
    private String id;

    /**
     * 模型版本号，格式 vX.Y.Z
     * 用于区分不同训练周期的模型评估结果
     */
    private String modelVersion;

    /**
     * 准确率 (Accuracy)
     * 预测正确的样本数占总样本数的比例，取值范围 [0, 1]
     */
    private Double accuracy;

    /**
     * F1分数 (F1-Score)
     * 精确率和召回率的调和平均值，取值范围 [0, 1]
     */
    private Double f1Score;

    /**
     * 精确率 (Precision)
     * 预测为正类的样本中实际为正类的比例，取值范围 [0, 1]
     */
    private Double precision;

    /**
     * 召回率 (Recall)
     * 实际为正类的样本中被正确预测的比例，取值范围 [0, 1]
     */
    private Double recall;

    /**
     * ROC-AUC (Receiver Operating Characteristic - Area Under Curve)
     * 衡量分类模型区分能力的指标，取值范围 [0, 1]，越接近1越好
     */
    private Double rocAuc;

    /**
     * 夏普比率 (Sharpe Ratio)
     * 衡量策略风险调整后收益的指标，值越大表示策略性价比越高
     */
    private Double sharpeRatio;

    /**
     * 方向准确率 (Direction Accuracy)
     * 预测股价涨跌方向与实际涨跌方向一致的比例，取值范围 [0, 1]
     */
    private Double directionAccuracy;

    /**
     * 最大回撤 (Maximum Drawdown)
     * 策略历史最大亏损比例，取值范围 [0, 1]，越小越好
     */
    private Double maxDrawdown;

    /**
     * 评估使用的样本数量
     */
    private Integer sampleCount;

    /**
     * 阈值状态
     * <ul>
     *   <li>normal - 指标正常</li>
     *   <li>warning - 指标接近阈值，需要关注</li>
     *   <li>critical - 指标超出阈值，需要告警</li>
     * </ul>
     */
    private String thresholdStatus;

    /**
     * 触发评估的来源
     * <ul>
     *   <li>scheduled - 定时任务触发</li>
     *   <li>manual - 手动触发</li>
     *   <li>threshold - 阈值告警触发</li>
     * </ul>
     */
    private String triggerSource;

    /**
     * 评估完成时间
     */
    private LocalDateTime createdAt;
}