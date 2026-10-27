// AI_GENERATE_START-----
package com.stock.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

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
public class ModelOverviewResponse {

    /** 模型逻辑名称。 */
    private String modelName;

    /** 当前激活版本。 */
    private ModelVersionResponse activeVersion;

    /** 上一可回滚版本。 */
    private ModelVersionResponse previousVersion;

    /** 最近一次训练运行。 */
    private ModelTrainingRunResponse latestTrainingRun;

    /** 是否存在运行中或排队中的训练。 */
    private boolean trainingRunning;

    /** 当前激活模型是否通过完整性和兼容性校验。 */
    private boolean activeModelHealthy;

    /** 当前激活版本最近一次有效评估的评分摘要。 */
    private ModelScoreSummaryResponse activeVersionScore;

    /** Ranking LSTM 与 MASTER 影子评估和融合状态。 */
    private TemporalModelEnsembleStatusResponse temporalEnsemble;
}
// AI_GENERATE_END-----
