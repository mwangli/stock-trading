// AI_GENERATE_START -------
package com.stock.modelService.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 模型预测批次、明细和能力评估接口 DTO 集合。
 *
 * @author mwangli
 * @since 2026-10-12
 */
public final class ModelPredictionAnalysisDto {

    private ModelPredictionAnalysisDto() {
    }

    /**
     * 刷新实际结果请求。
     *
     * @author mwangli
     * @since 2026-10-12
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EvaluatePredictionRequest {

        /** 预测批次 ID；为空时评估最新批次。 */
        private String batchId;
    }

    /**
     * 历史预测重算请求。
     *
     * @author mwangli
     * @since 2026-10-18
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RerunPredictionRequest {

        /** 原历史预测批次 ID。 */
        private String batchId;

        /** 发起重算的操作人名称。 */
        private String operatorName;
    }

    /**
     * 预测操作响应。
     *
     * @author mwangli
     * @since 2026-10-12
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OperationResponse {

        /** 预测批次 ID。 */
        private String batchId;

        /** 当前批次状态。 */
        private String status;

        /** 操作结果说明。 */
        private String message;
    }

    /**
     * 预测批次列表项。
     *
     * @author mwangli
     * @since 2026-10-12
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BatchItem {

        /** 预测批次 ID。 */
        private String id;

        /** 模型逻辑名称。 */
        private String modelName;

        /** 模型文档 ID。 */
        private String modelId;

        /** 模型业务版本。 */
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

        /** 评估队列状态。 */
        private String evaluationQueueStatus;

        /** 评估等待原因。 */
        private String evaluationWaitReason;

        /** 评估任务进入队列的时间。 */
        private LocalDateTime evaluationQueuedAt;

        /** 评估任务开始执行的时间。 */
        private LocalDateTime evaluationStartedAt;

        /** 评估任务完成的时间。 */
        private LocalDateTime evaluationFinishedAt;

        /** 最近一次评估队列执行失败原因。 */
        private String evaluationErrorMessage;

        /** 预测覆盖率。 */
        private Double coverageRate;

        /** 评估完成率。 */
        private Double evaluationRate;

        /** 模型能力等级。 */
        private String abilityLevel;

        /** 是否建议优化迭代模型。 */
        private boolean iterationRecommended;

        /** 能力结论说明。 */
        private String abilityMessage;

        /** 交易门禁状态。 */
        private String tradingEligibility;

        /** 交易门禁原因。 */
        private String tradingEligibilityReason;

        /** 批次进度说明。 */
        private String progressMessage;

        /** 操作人。 */
        private String triggeredBy;

        /** 执行开始时间。 */
        private LocalDateTime startedAt;

        /** 执行完成时间。 */
        private LocalDateTime finishedAt;

        /** 失败原因。 */
        private String errorMessage;

        /** 文档创建时间。 */
        private LocalDateTime createdAt;

        /** 最近更新时间。 */
        private LocalDateTime updatedAt;
    }

    /**
     * 预测批次分页响应。
     *
     * @author mwangli
     * @since 2026-10-12
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BatchPageResponse {

        /** 当前页预测批次。 */
        private List<BatchItem> items;

        /** 总记录数。 */
        private long total;

        /** 当前页码。 */
        private int current;

        /** 每页大小。 */
        private int pageSize;
    }

    /**
     * 单股预测与实际结果对比项。
     *
     * @author mwangli
     * @since 2026-10-12
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PredictionItem {

        /** 预测明细 ID。 */
        private String id;

        /** 所属预测批次 ID。 */
        private String batchId;

        /** 股票代码。 */
        private String stockCode;

        /** 股票名称。 */
        private String stockName;

        /** 行业代码。 */
        private Integer industryCode;

        /** 模型文档 ID。 */
        private String modelId;

        /** 模型业务版本。 */
        private String modelVersion;

        /** 基准交易日。 */
        private LocalDate baseTradeDate;

        /** 目标交易日。 */
        private LocalDate targetTradeDate;

        /** 目标交易日是否由工作日规则降级推算。 */
        private Boolean calendarFallback;

        /** 基准日收盘价。 */
        private Double baseClosePrice;

        /** 预测收盘价。 */
        private Double predictedClosePrice;

        /** 预测收益率。 */
        private Double predictedReturn;

        /** 上涨概率。 */
        private Double directionProbability;

        /** 下行风险。 */
        private Double downsideRisk;

        /** 预测收益率横截面百分位。 */
        private Double predictedReturnPercentile;

        /** 上涨概率横截面百分位。 */
        private Double directionProbabilityPercentile;

        /** 实际收盘价。 */
        private Double actualClosePrice;

        /** 实际收益率。 */
        private Double actualReturn;

        /** 预测收益减实际收益。 */
        private Double returnError;

        /** 收益绝对误差。 */
        private Double absoluteError;

        /** 方向是否命中。 */
        private Boolean directionCorrect;

        /** 评估状态。 */
        private String evaluationStatus;

        /** 预测生成时间。 */
        private LocalDateTime predictedAt;

        /** 实际结果评估时间。 */
        private LocalDateTime evaluatedAt;
    }

    /**
     * 预测明细分页响应。
     *
     * @author mwangli
     * @since 2026-10-12
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PredictionPageResponse {

        /** 当前页预测明细。 */
        private List<PredictionItem> items;

        /** 总记录数。 */
        private long total;

        /** 当前页码。 */
        private int current;

        /** 每页大小。 */
        private int pageSize;
    }

    /**
     * 模型能力评估响应。
     *
     * @author mwangli
     * @since 2026-10-12
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AbilityResponse {

        /** 预测批次 ID。 */
        private String batchId;

        /** 模型文档 ID。 */
        private String modelId;

        /** 模型业务版本。 */
        private String modelVersion;

        /** 预测基准交易日。 */
        private LocalDate baseTradeDate;

        /** 预测目标交易日。 */
        private LocalDate targetTradeDate;

        /** 成功预测数量。 */
        private int predictedCount;

        /** 已评估数量。 */
        private int evaluatedCount;

        /** 预测覆盖率。 */
        private Double coverageRate;

        /** 评估完成率。 */
        private Double evaluationRate;

        /** 方向准确率。 */
        private Double directionAccuracy;

        /** 实际上涨比例。 */
        private Double actualUpRate;

        /** 常数方向基线准确率。 */
        private Double constantBaselineAccuracy;

        /** 方向上涨概率的 ROC AUC；实际标签只有单一类别时为空。 */
        private Double directionAuc;

        /** 上涨概率 Brier Score，越低越好。 */
        private Double brierScore;

        /** 固定上涨比例的 Brier 基线。 */
        private Double brierBaseline;

        /** 上涨概率期望校准误差。 */
        private Double expectedCalibrationError;

        /** 概率校准有效样本数。 */
        private Integer calibrationSampleCount;

        /** 上涨概率标准差。 */
        private Double directionProbabilityStd;

        /** 预测收益率标准差。 */
        private Double predictedReturnStd;

        /** 最近滚动窗口批次数。 */
        private Integer rollingWindowBatchCount;

        /** 最近滚动窗口有效样本数。 */
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

        /** Top 10% 股票实际平均收益率。 */
        private Double topDecileAverageReturn;

        /** Top 10% 股票上涨命中率。 */
        private Double topDecileDirectionAccuracy;

        /** 高置信度方向准确率。 */
        private Double highConfidenceAccuracy;

        /** 高下行风险样本实际下跌识别率。 */
        private Double downsideRiskHitRate;

        /** 能力等级。 */
        private String abilityLevel;

        /** 是否建议优化迭代模型。 */
        private boolean iterationRecommended;

        /** 能力结论说明。 */
        private String abilityMessage;

        /** 交易门禁状态。 */
        private String tradingEligibility;

        /** 交易门禁原因。 */
        private String tradingEligibilityReason;
    }
}
// AI_GENERATE_END -------
