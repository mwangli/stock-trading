// AI_GENERATE_START --
package com.stock.tradingExecutor.domain.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 券商账户资金只读结果。
 *
 * @author mwangli
 * @since 2026-09-25
 */
@Data
@Builder
public class BrokerAccountDto {

    /** 总资产，单位为元。 */
    private BigDecimal totalAssets;

    /** 可用资金，单位为元。 */
    private BigDecimal availableCash;

    /** 冻结资金，单位为元。 */
    private BigDecimal frozenAmount;

    /** 持仓市值，单位为元。 */
    private BigDecimal totalPosition;

    /** 查询时间。 */
    private LocalDateTime capturedAt;
}
// AI_GENERATE_END --
