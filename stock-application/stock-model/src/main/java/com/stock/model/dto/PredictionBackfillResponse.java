// AI_GENERATE_START---
package com.stock.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 预测与评估缺口扫描响应。
 * 返回本次交易日扫描、预测入队和评估入队的汇总结果。
 *
 * @author mwangli
 * @since 2026-10-22
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PredictionBackfillResponse {

    /** 本次扫描的交易日数量。 */
    private int scannedTradingDays;

    /** 已存在有效预测或已在队列中的交易日数量。 */
    private int existingPredictionCount;

    /** 新增预测队列批次数量。 */
    private int predictionQueuedCount;

    /** 因基准日行情覆盖率不足而暂未入队的交易日数量。 */
    private int predictionWaitingForMarketDataCount;

    /** 新增评估队列批次数量。 */
    private int evaluationQueuedCount;

    /** 首个新入队预测批次 ID，无新增批次时为空。 */
    private String batchId;

    /** 队列提交状态。 */
    private String status;

    /** 操作结果说明。 */
    private String message;
}
// AI_GENERATE_END---
