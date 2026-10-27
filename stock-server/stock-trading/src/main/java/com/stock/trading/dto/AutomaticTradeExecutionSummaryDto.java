// AI_GENERATE_START- -
package com.stock.trading.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 自动交易执行批次只读摘要。
 *
 * @author mwangli
 * @since 2026-10-24
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AutomaticTradeExecutionSummaryDto {
    /** 执行批次业务标识。 */
    private String batchId;
    /** 执行状态。 */
    private String status;
    /** 已处理数量。 */
    private int processedCount;
    /** 成功数量。 */
    private int successCount;
}
// AI_GENERATE_END- -
