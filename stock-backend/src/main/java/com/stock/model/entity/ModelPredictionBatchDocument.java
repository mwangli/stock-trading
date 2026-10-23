// AI_GENERATE_START----------
package com.stock.model.entity;

import lombok.Data;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import org.hibernate.annotations.UuidGenerator;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 全市场模型预测批次实体。
 * 记录批次绑定的模型版本、交易日、执行进度和能力评估汇总。
 *
 * @author mwangli
 * @since 2026-10-12
 */
@Data
@Entity
@Table(name = "model_prediction_batch", indexes = {
        @Index(name = "idx_prediction_batch_model_date", columnList = "model_id, base_trade_date"),
        @Index(name = "idx_prediction_queue_order", columnList = "status, target_trade_date, created_at"),
        @Index(name = "idx_evaluation_queue_order",
                columnList = "evaluation_queue_status, target_trade_date, created_at")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uk_prediction_batch_rerun_source", columnNames = "rerun_source_batch_id")
})
public class ModelPredictionBatchDocument {

    /** 数据库主键。 */
    @Id
    @GeneratedValue
    @UuidGenerator
    private String id;

    /** 模型逻辑名称。 */
    private String modelName;

    /** 本批次固定使用的模型文档 ID。 */
    private String modelId;

    /** 历史重算来源批次 ID；普通预测批次为空。 */
    private String rerunSourceBatchId;

    /** 模型业务版本号。 */
    private String modelVersion;

    /** 预测基准交易日。 */
    private LocalDate baseTradeDate;

    /** 预测目标交易日。 */
    private LocalDate targetTradeDate;

    /** 本批次允许使用信息的自然时间截止点。 */
    private LocalDateTime informationCutoffAt;

    /** 目标交易日是否由工作日规则降级推算。 */
    private Boolean calendarFallback;

    /** 批次状态。 */
    private String status;

    /** 股票池总数。 */
    private int totalStocks;

    /** 已处理股票数。 */
    private int processedStocks;

    /** 成功预测数量。 */
    private int successCount;

    /** 失败预测数量。 */
    private int failedCount;

    /** 已评估数量。 */
    private int evaluatedCount;

    /** 缺少目标日行情数量。 */
    private int noMarketDataCount;

    /** 评估队列状态：WAITING、QUEUED、RUNNING、COMPLETED 或 FAILED。 */
    private String evaluationQueueStatus;

    /** 评估等待原因代码：预测执行中、目标日未到达或目标日行情覆盖不足。 */
    private String evaluationWaitReason;

    /** 评估任务进入队列的时间。 */
    private LocalDateTime evaluationQueuedAt;

    /** 评估任务开始执行的时间。 */
    private LocalDateTime evaluationStartedAt;

    /** 评估任务完成的时间。 */
    private LocalDateTime evaluationFinishedAt;

    /** 最近一次评估队列执行失败原因。 */
    @Lob
    private String evaluationErrorMessage;

    /** 预测覆盖率。 */
    private Double coverageRate;

    /** 评估完成率。 */
    private Double evaluationRate;

    /** 方向准确率。 */
    private Double directionAccuracy;

    /** 实际上涨比例，用于构造常数方向基线。 */
    private Double actualUpRate;

    /** 始终预测多数方向时的准确率基线。 */
    private Double constantBaselineAccuracy;

    /** 方向上涨概率的 ROC AUC；实际标签只有单一类别时为空。 */
    private Double directionAuc;

    /** 上涨概率 Brier Score，越低越好。 */
    private Double brierScore;

    /** 固定使用实际上涨比例的 Brier 基线，越低越好。 */
    private Double brierBaseline;

    /** 上涨概率期望校准误差，越低越好。 */
    private Double expectedCalibrationError;

    /** 参与概率校准指标计算的有效样本数。 */
    private Integer calibrationSampleCount;

    /** 上涨概率均值。 */
    private Double directionProbabilityMean;

    /** 上涨概率标准差，用于检测输出塌缩。 */
    private Double directionProbabilityStd;

    /** 上涨概率最小值。 */
    private Double directionProbabilityMin;

    /** 上涨概率最大值。 */
    private Double directionProbabilityMax;

    /** 预测收益率标准差，用于衡量横截面区分度。 */
    private Double predictedReturnStd;

    /** 最近滚动评估窗口包含的批次数。 */
    private Integer rollingWindowBatchCount;

    /** 最近滚动评估窗口的有效样本数。 */
    private Integer rollingWindowSampleCount;

    /** 最近滚动窗口平均排序 IC。 */
    private Double rollingAverageSpearmanRankIc;

    /** 最近滚动窗口 Top 10% 平均收益。 */
    private Double rollingAverageTopDecileReturn;

    /** 收益率平均绝对误差。 */
    private Double returnMae;

    /** 收益率均方根误差。 */
    private Double returnRmse;

    /** Pearson 横截面相关系数。 */
    private Double pearsonIc;

    /** Spearman 排序相关系数。 */
    private Double spearmanRankIc;

    /** 预测收益最高 10% 股票的实际平均收益率。 */
    private Double topDecileAverageReturn;

    /** 预测收益最高 10% 股票的上涨命中率。 */
    private Double topDecileDirectionAccuracy;

    /** 高置信度方向准确率。 */
    private Double highConfidenceAccuracy;

    /** 高下行风险样本的实际下跌识别率。 */
    private Double downsideRiskHitRate;

    /** 模型能力等级。 */
    private String abilityLevel;

    /** 是否建议进入模型优化评估。 */
    private boolean iterationRecommended;

    /** 能力结论说明。 */
    @Lob
    private String abilityMessage;

    /** 交易门禁状态：DISABLED、OBSERVE_ONLY 或 TRADE_ELIGIBLE。 */
    private String tradingEligibility;

    /** 交易门禁原因，禁止仅以能力等级代替交易准入结论。 */
    @Lob
    private String tradingEligibilityReason;

    /** 批次进度说明。 */
    @Lob
    private String progressMessage;

    /** 操作人。 */
    private String triggeredBy;

    /** 执行开始时间。 */
    private LocalDateTime startedAt;

    /** 执行完成时间。 */
    private LocalDateTime finishedAt;

    /** 失败原因。 */
    @Lob
    private String errorMessage;

    /** 记录创建时间。 */
    private LocalDateTime createdAt;

    /** 最近更新时间。 */
    private LocalDateTime updatedAt;

    /** 乐观锁版本。 */
    @Version
    private Long version;
}
// AI_GENERATE_END----------
