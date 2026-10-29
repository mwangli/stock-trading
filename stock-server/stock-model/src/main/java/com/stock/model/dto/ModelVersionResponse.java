// AI_GENERATE_START--------
package com.stock.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

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

    /** 正式模型代际。 */
    private String modelGeneration;

    /** 每个时间步的总输入维度。 */
    private Integer inputSize;

    /** 免费行情和市场连续特征数量。 */
    private Integer marketFeatureCount;

    /** 新闻连续特征数量。 */
    private Integer newsFeatureCount;

    /** 连续特征总数。 */
    private Integer continuousFeatureCount;

    /** 类别索引特征数量。 */
    private Integer categoryFeatureCount;

    /** 模型行情来源。 */
    private String dataSource;

    /** 模型行情来源版本。 */
    private String dataSourceVersion;

    /** 价格连续化口径。 */
    private String priceBasis;

    /** 市场基准构造口径。 */
    private String benchmarkType;

    /** 当前能力等级。 */
    private String capabilityLevel;

    /** 当前免费数据契约的能力限制。 */
    private List<String> limitations;

    /** 后续付费数据增强目标。 */
    private String upgradeTarget;

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

    /** Walk-Forward Top 10% 毛收益。 */
    private Double walkForwardTopDecileGrossReturn;

    /** Walk-Forward 逐月复合毛收益。 */
    private Map<String, Double> walkForwardMonthlyGrossReturns;

    /** Walk-Forward 月均复合毛收益。 */
    private Double walkForwardMonthlyGrossReturnMean;

    /** Walk-Forward 月度毛收益标准差。 */
    private Double walkForwardMonthlyGrossReturnStandardDeviation;

    /** Walk-Forward 正毛收益月份占比。 */
    private Double walkForwardPositiveMonthRatio;

    /** Walk-Forward 最差月份毛收益。 */
    private Double walkForwardWorstMonthlyGrossReturn;

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
// AI_GENERATE_END--------
