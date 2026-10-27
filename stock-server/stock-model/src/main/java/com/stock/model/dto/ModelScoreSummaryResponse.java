// AI_GENERATE_START----
package com.stock.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * 当前生产模型最近一次有效评估的评分摘要。
 * 用于模型运维概览展示版本质量，不承载预测明细或交易准入决策。
 *
 * @author mwangli
 * @since 2026-10-22
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ModelScoreSummaryResponse {

    /** 评分来源预测批次 ID，不可为空。 */
    private String batchId;

    /** 评分对应的模型文档 ID，不可为空。 */
    private String modelId;

    /** 评分对应的模型业务版本，不可为空。 */
    private String modelVersion;

    /** 评分对应的目标交易日，格式 yyyy-MM-dd，可为空。 */
    private LocalDate targetTradeDate;

    /** 已完成评估的样本数量，单位为只，最小值为 1。 */
    private int evaluatedCount;

    /** 预测覆盖率，范围 0-1，可为空。 */
    private Double coverageRate;

    /** 全部已评估样本的方向准确率，范围 0-1，可为空。 */
    private Double directionAccuracy;

    /** 预测收益排名前 10% 股票的上涨命中率，范围 0-1，可为空。 */
    private Double topDecileDirectionAccuracy;

    /** 高置信度方向预测的命中率，范围 0-1，可为空。 */
    private Double highConfidenceAccuracy;

    /** 预测收益排序与实际收益排序的 Spearman 相关系数，范围 -1 至 1，可为空。 */
    private Double spearmanRankIc;

    /** 模型能力等级，可为空。 */
    private String abilityLevel;
}
// AI_GENERATE_END----
