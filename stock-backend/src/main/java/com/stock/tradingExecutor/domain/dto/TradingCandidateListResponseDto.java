// AI_GENERATE_START -
package com.stock.tradingExecutor.domain.dto;

import lombok.Builder;
import lombok.Data;

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

    /** 候选股票列表。 */
    private List<TradingCandidateDto> items;
}
// AI_GENERATE_END -
