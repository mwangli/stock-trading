// AI_GENERATE_START------
package com.stock.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

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
public class ModelVersionResponse {

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

    /** 模型架构类型。 */
    private String architectureType;

    /** 输出契约版本。 */
    private String outputSchemaVersion;

    /** Walk-Forward 平均 Rank IC。 */
    private Double walkForwardMeanRankIc;

    /** Walk-Forward Rank ICIR。 */
    private Double walkForwardRankIcIr;

    /** Rank IC 为正的测试窗口占比。 */
    private Double positiveRankIcWindowRatio;

    /** Walk-Forward Top 10% 成本后收益。 */
    private Double walkForwardTopDecileNetReturn;

    /** 温度校准后的 Brier Score。 */
    private Double brierScore;

    /** 实际上涨比例基线 Brier Score。 */
    private Double brierBaseline;

    /** 是否检测到输出塌缩。 */
    private Boolean outputCollapsed;

    /** 版本创建时间。 */
    private LocalDateTime createdAt;

    /** 是否为当前激活版本。 */
    private boolean active;

    /** 是否为上一可回滚版本。 */
    private boolean previous;
}
// AI_GENERATE_END------
