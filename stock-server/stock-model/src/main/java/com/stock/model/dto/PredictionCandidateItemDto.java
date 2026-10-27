// AI_GENERATE_START---------
package com.stock.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 单只股票预测明细。
 *
 * @author mwangli
 * @since 2026-10-14
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PredictionCandidateItemDto {

    /** 股票代码。 */
    private String stockCode;

    /** 股票名称。 */
    private String stockName;

    /** 模型输入基准日的收盘价，作为页面展示的股票价格。 */
    private Double currentPrice;

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

    /** `V6` 横截面排序原始分数。 */
    private Double rankScore;

    /** 排序分数横截面百分位。 */
    private Double crossSectionPercentile;

    /** 预测市场和行业中性后的超额收益率。 */
    private Double expectedExcessReturn;

    /** 经模型制品参数校准后的上涨概率。 */
    private Double calibratedUpProbability;

    /** 预测下行风险分位。 */
    private Double downsideQuantile;

    /** 双模型融合后的生产有效横截面排序分数。 */
    private Double effectiveRankScore;

    /** 双模型融合后的生产有效预期超额收益。 */
    private Double effectiveExpectedExcessReturn;

    /** 双模型融合后的生产有效上涨概率。 */
    private Double effectiveUpProbability;

    /** 生产有效上涨概率在当日横截面中的百分位。 */
    private Double effectiveUpProbabilityPercentile;

    /** 双模型融合后的生产有效下行风险分位。 */
    private Double effectiveDownsideQuantile;

    /** 按统一四任务公式计算的生产最终模型评分。 */
    private Double effectiveModelScore;

    /** 生产有效预期超额收益扣除交易成本和安全边际后的净收益。 */
    private Double effectiveEstimatedNetReturn;
}
// AI_GENERATE_END---------
