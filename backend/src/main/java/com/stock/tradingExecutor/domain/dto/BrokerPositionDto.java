// AI_GENERATE_START --
package com.stock.tradingExecutor.domain.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 券商持仓只读结果。
 *
 * @author mwangli
 * @since 2026-09-25
 */
@Data
@Builder
public class BrokerPositionDto {

    /** 股票代码。 */
    private String stockCode;

    /** 股票名称。 */
    private String stockName;

    /** 总持仓数量。 */
    private Integer totalQuantity;

    /** 当前可卖数量。 */
    private Integer availableQuantity;

    /** 冻结数量。 */
    private Integer frozenQuantity;

    /** 平均持仓成本，单位为元。 */
    private BigDecimal averageCost;

    /** 当前价格，单位为元。 */
    private BigDecimal currentPrice;

    /** 当前市值，单位为元。 */
    private BigDecimal marketValue;

    /** 市场或交易类别。 */
    private String market;

    /** 脱敏后的股东账号。 */
    private String shareholderAccountMasked;
}
// AI_GENERATE_END --
