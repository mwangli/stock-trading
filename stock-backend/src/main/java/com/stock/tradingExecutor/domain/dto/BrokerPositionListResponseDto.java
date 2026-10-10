// AI_GENERATE_START --
package com.stock.tradingExecutor.domain.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 券商持仓列表响应。
 *
 * @author mwangli
 * @since 2026-09-25
 */
@Data
@Builder
public class BrokerPositionListResponseDto {

    /** 持仓明细。 */
    private List<BrokerPositionDto> items;

    /** 持仓条数。 */
    private int total;

    /** 查询时间。 */
    private LocalDateTime capturedAt;
}
// AI_GENERATE_END --
