// AI_GENERATE_START---
package com.stock.platform.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 单个交易建议评估所需的权威事实。
 *
 * @param candidateBatchId 候选批次标识
 * @param stockCode 股票代码
 * @param targetTradeDate 目标交易日
 * @param targetOpenPrice 目标日开盘价，单位元
 * @param targetHighPrice 目标日最高价，单位元
 * @param targetLowPrice 目标日最低价，单位元
 * @param targetClosePrice 目标日收盘价，单位元
 * @param executionBatchId 自动交易执行批次标识
 * @param executionBatchStatus 执行批次状态
 * @param executionProcessedCount 已处理数量
 * @param executionSuccessCount 成功数量
 * @author mwangli
 * @since 2026-10-23
 */
public record InternalTradeDecisionEvaluationFactResponse(
        String candidateBatchId, String stockCode, LocalDate targetTradeDate,
        BigDecimal targetOpenPrice, BigDecimal targetHighPrice,
        BigDecimal targetLowPrice, BigDecimal targetClosePrice,
        String executionBatchId, String executionBatchStatus,
        Integer executionProcessedCount, Integer executionSuccessCount) {
}
// AI_GENERATE_END---
