// AI_GENERATE_START ---
package com.stock.tradingExecutor.domain.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 当日真实交易候选列表。
 *
 * @author mwangli
 * @since 2026-09-25
 */
@Data
@Builder
public class TradingCandidateListResponseDto {

    /** 候选批次 ID。 */
    private String candidateBatchId;

    /** 来源预测批次 ID。 */
    private String predictionBatchId;

    /** LSTM 模型业务版本。 */
    private String modelVersion;

    /** 情感模型版本。 */
    private String sentimentModelVersion;

    /** 策略配置版本。 */
    private String strategyConfigVersion;

    /** 模型输入基准交易日。 */
    private LocalDate baseTradeDate;

    /** 候选目标交易日。 */
    private LocalDate targetTradeDate;

    /** 候选信息截止时间。 */
    private LocalDateTime informationCutoffAt;

    /** 是否已获取真实账户持仓快照。 */
    private boolean positionSnapshotAvailable;

    /** 候选股票列表。 */
    private List<TradingCandidateDto> items;
}
// AI_GENERATE_END ---
