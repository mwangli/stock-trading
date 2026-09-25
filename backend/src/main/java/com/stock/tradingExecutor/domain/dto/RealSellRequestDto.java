// AI_GENERATE_START -
package com.stock.tradingExecutor.domain.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 真实卖出请求。
 *
 * @author mwangli
 * @since 2026-09-25
 */
@Data
public class RealSellRequestDto {

    /** 股票代码。 */
    private String stockCode;

    /** 卖出数量，必须是正整数。 */
    private BigDecimal quantity;
}
// AI_GENERATE_END -
