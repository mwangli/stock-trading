// AI_GENERATE_START--------
package com.stock.model.dto;

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

    /** 当前 Ranking LSTM 模型文档 ID。 */
    private String rankingLstmModelId;

    /** 当前 MASTER 模型文档 ID，可为空。 */
    private String masterModelId;

    /** 本批次实际使用的 Ranking LSTM 权重。 */
    private double rankingLstmWeight;

    /** 本批次实际使用的 MASTER 权重。 */
    private double masterWeight;

    /** 本批次使用的自动权重版本。 */
    private long weightVersion;

    /** 本批次的融合运行状态。 */
    private String ensembleStatus;

    /** 推理输入基准交易日。 */
    private LocalDate baseTradeDate;

    /** 预测目标交易日。 */
    private LocalDate targetTradeDate;

    /** 预测批次信息截止时间。 */
    private LocalDateTime informationCutoffAt;

    /** 模型交易门禁状态。 */
    private String tradingEligibility;

    /** 模型交易门禁原因。 */
    private String tradingEligibilityReason;

    /** 可用于策略排序的预测明细。 */
    private List<PredictionCandidateItemDto> items;

    /** 融合模型是否已按配置的新闻事件特征版本完成重训。 */
    private boolean newsFeaturesEmbedded;

}
// AI_GENERATE_END--------
