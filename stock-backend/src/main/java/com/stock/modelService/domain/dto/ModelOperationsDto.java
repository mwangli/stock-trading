// AI_GENERATE_START -
package com.stock.modelService.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 模型运维接口 DTO 集合。
 * 统一定义模型概览、版本、训练运行和高风险操作的请求响应结构。
 *
 * @author mwangli
 * @since 2026-10-10
 */
public final class ModelOperationsDto {

    private ModelOperationsDto() {
    }

    /**
     * 模型运维概览响应。
     *
     * @author mwangli
     * @since 2026-10-10
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OverviewResponse {

        /** 模型逻辑名称。 */
        private String modelName;

        /** 当前激活版本。 */
        private ModelVersionItem activeVersion;

        /** 上一可回滚版本。 */
        private ModelVersionItem previousVersion;

        /** 最近一次训练运行。 */
        private TrainingRunItem latestTrainingRun;

        /** 当前节点是否开启训练总开关。 */
        private boolean trainingEnabled;

        /** 当前时刻是否允许提交训练。 */
        private boolean trainingAllowed;

        /** 是否存在运行中或排队中的训练。 */
        private boolean trainingRunning;

        /** 当前是否处于交易时段。 */
        private boolean tradingTime;

        /** 当前激活模型是否通过完整性和兼容性校验。 */
        private boolean activeModelHealthy;

        /** 当前训练门禁说明。 */
        private String gateMessage;
    }

    /**
     * 模型版本列表项和详情响应。
     *
     * @author mwangli
     * @since 2026-10-10
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ModelVersionItem {

        /** MongoDB 版本文档 ID。 */
        private String id;

        /** 模型逻辑名称。 */
        private String modelName;

        /** 模型版本号。 */
        private String modelVersion;

        /** 父版本文档 ID。 */
        private String parentModelVersionId;

        /** 版本状态。 */
        private String status;

        /** 特征版本。 */
        private String featureVersion;

        /** 标签版本。 */
        private String labelVersion;

        /** 模型执行引擎名称。 */
        private String engineName;

        /** 模型执行引擎版本。 */
        private String engineVersion;

        /** DJL 版本。 */
        private String djlVersion;

        /** 参数 SHA-256 摘要。 */
        private String parameterSha256;

        /** 参数字节数。 */
        private long parameterSize;

        /** 最佳训练轮次。 */
        private int epoch;

        /** 最佳训练损失。 */
        private Double trainLoss;

        /** 最佳验证损失。 */
        private Double valLoss;

        /** 训练配置 JSON 快照。 */
        private String trainingConfigJson;

        /** 输入契约 JSON 快照。 */
        private String inputContractJson;

        /** 模型指标 JSON 快照。 */
        private String metricsJson;

        /** 版本创建时间。 */
        private LocalDateTime createdAt;

        /** 是否为当前激活版本。 */
        private boolean active;

        /** 是否为上一可回滚版本。 */
        private boolean previous;
    }

    /**
     * 模型版本分页响应。
     *
     * @author mwangli
     * @since 2026-10-10
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ModelVersionPageResponse {

        /** 当前页版本数据。 */
        private List<ModelVersionItem> items;

        /** 总记录数。 */
        private long total;

        /** 当前页码，从 1 开始。 */
        private int current;

        /** 每页大小。 */
        private int pageSize;
    }

    /**
     * 训练运行列表项。
     *
     * @author mwangli
     * @since 2026-10-10
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TrainingRunItem {

        /** 训练运行 ID。 */
        private String id;

        /** 模型逻辑名称。 */
        private String modelName;

        /** 训练运行状态。 */
        private String status;

        /** 触发来源。 */
        private String triggerSource;

        /** 触发操作人。 */
        private String triggeredBy;

        /** 请求参数 JSON。 */
        private String requestedConfigJson;

        /** 实际生效参数 JSON。 */
        private String effectiveConfigJson;

        /** 门禁结果 JSON。 */
        private String gateResultJson;

        /** 开始时间。 */
        private LocalDateTime startedAt;

        /** 结束时间。 */
        private LocalDateTime finishedAt;

        /** 候选模型版本 ID。 */
        private String candidateModelVersionId;

        /** 失败原因。 */
        private String errorMessage;

        /** 训练样本数量。 */
        private Integer trainSamples;

        /** 验证样本数量。 */
        private Integer valSamples;

        /** 最佳训练损失。 */
        private Double trainLoss;

        /** 最佳验证损失。 */
        private Double valLoss;

        /** 记录创建时间。 */
        private LocalDateTime createdAt;
    }

    /**
     * 训练运行分页响应。
     *
     * @author mwangli
     * @since 2026-10-10
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TrainingRunPageResponse {

        /** 当前页训练运行数据。 */
        private List<TrainingRunItem> items;

        /** 总记录数。 */
        private long total;

        /** 当前页码，从 1 开始。 */
        private int current;

        /** 每页大小。 */
        private int pageSize;
    }

    /**
     * 启动模型训练请求。
     *
     * @author mwangli
     * @since 2026-10-10
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StartTrainingRequest {

        /** 每只股票读取的交易日数量。 */
        private Integer days;

        /** 训练轮次覆盖值。 */
        private Integer epochs;

        /** 批次大小覆盖值。 */
        private Integer batchSize;

        /** 学习率覆盖值。 */
        private Double learningRate;

        /** 操作人名称。 */
        private String operatorName;
    }

    /**
     * 模型操作响应。
     *
     * @author mwangli
     * @since 2026-10-10
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OperationResponse {

        /** 操作类型。 */
        private String operationType;

        /** 训练运行或模型版本 ID。 */
        private String operationId;

        /** 操作结果说明。 */
        private String message;
    }
}
// AI_GENERATE_END -
