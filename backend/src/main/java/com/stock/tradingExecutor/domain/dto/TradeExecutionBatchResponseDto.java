// AI_GENERATE_START -
package com.stock.tradingExecutor.domain.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 批量真实交易执行结果。
 *
 * @author mwangli
 * @since 2026-09-25
 */
@Data
@Builder
public class TradeExecutionBatchResponseDto {

    /** 实际执行的委托结果。 */
    private List<TradeExecutionResponseDto> items;

    /** 成功完成执行流程的数量。 */
    private int successCount;

    /** 执行结果摘要。 */
    private String message;
}
// AI_GENERATE_END -
