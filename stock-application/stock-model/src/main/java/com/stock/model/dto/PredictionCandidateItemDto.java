// AI_GENERATE_START---
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

    /** 扣除费用、双边滑点和安全边际后的净预期收益率。 */
    private Double estimatedNetExpectedReturn;

    /** 上涨概率。 */
    private Double directionProbability;

    /** 下行风险。 */
    private Double downsideRisk;

    /** 预测收益率横截面百分位。 */
    private Double predictedReturnPercentile;

    /** 上涨概率横截面百分位。 */
    private Double directionProbabilityPercentile;
}
// AI_GENERATE_END---
