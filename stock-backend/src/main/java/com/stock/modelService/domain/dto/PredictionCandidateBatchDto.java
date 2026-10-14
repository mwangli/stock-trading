// AI_GENERATE_START -
package com.stock.modelService.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 策略候选使用的已落库预测批次 DTO。
 *
 * @author mwangli
 * @since 2026-10-14
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PredictionCandidateBatchDto {

    /** 预测批次 ID。 */
    private String batchId;

    /** 模型文档 ID。 */
    private String modelId;

    /** 模型业务版本。 */
    private String modelVersion;

    /** 推理输入基准交易日。 */
    private LocalDate baseTradeDate;

    /** 预测目标交易日。 */
    private LocalDate targetTradeDate;

    /** 预测批次信息截止时间。 */
    private LocalDateTime informationCutoffAt;

    /** 可用于策略排序的预测明细。 */
    private List<PredictionCandidateItemDto> items;

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
    public static class PredictionCandidateItemDto {

        /** 股票代码。 */
        private String stockCode;

        /** 股票名称。 */
        private String stockName;

        /** 预测收益率。 */
        private Double predictedReturn;

        /** 上涨概率。 */
        private Double directionProbability;

        /** 下行风险。 */
        private Double downsideRisk;
    }
}
// AI_GENERATE_END -
