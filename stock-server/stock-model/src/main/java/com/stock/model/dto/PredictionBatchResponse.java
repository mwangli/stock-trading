// AI_GENERATE_START----
package com.stock.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDate;
import java.time.LocalDateTime;

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
public class PredictionBatchResponse {

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
// AI_GENERATE_END----
