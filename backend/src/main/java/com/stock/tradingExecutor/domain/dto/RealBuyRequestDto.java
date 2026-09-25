// AI_GENERATE_START -
package com.stock.tradingExecutor.domain.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 真实买入请求。
 *
 * @author mwangli
 * @since 2026-09-25
 */
@Data
public class RealBuyRequestDto {

    /** 股票代码。 */
    private String stockCode;

    /** 计划投入金额，单位为元。 */
    private BigDecimal amount;

    /** 是否启用价格监控后再下单。 */
    private boolean monitorPrice;
}
// AI_GENERATE_END -
