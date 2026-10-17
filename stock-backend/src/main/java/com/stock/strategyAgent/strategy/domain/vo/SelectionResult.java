// AI_GENERATE_START -
package com.stock.strategyAgent.strategy.domain.vo;

import com.stock.strategyAgent.strategy.domain.dto.StockRankingDto;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 选股结果 VO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SelectionResult {

    private LocalDateTime executeTime;
    private int totalStocks;
    private List<StockRankingDto> topN;
    private long costTimeMs;
    private boolean success;
    private String errorMessage;
}
// AI_GENERATE_END -
