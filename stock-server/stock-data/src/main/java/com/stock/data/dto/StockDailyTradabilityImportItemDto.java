// AI_GENERATE_START-----
package com.stock.data.dto;

import com.stock.data.entity.OpenTradabilityStatus;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 单只股票单日开盘可成交性事实。
 *
 * @author mwangli
 * @since 2026-09-25
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockDailyTradabilityImportItemDto {

    /** 股票代码。 */
    @NotBlank
    private String stockCode;

    /** 交易日期。 */
    @NotNull
    private LocalDate tradeDate;

    /** 开盘买入可执行状态。 */
    @NotNull
    private OpenTradabilityStatus openBuyStatus;

    /** 开盘卖出可执行状态。 */
    @NotNull
    private OpenTradabilityStatus openSellStatus;

    /** 数据源确认的开盘价格。 */
    @DecimalMin(value = "0", inclusive = false)
    private BigDecimal observedOpenPrice;

    /** 当日涨停价格。 */
    @DecimalMin(value = "0", inclusive = false)
    private BigDecimal limitUpPrice;

    /** 当日跌停价格。 */
    @DecimalMin(value = "0", inclusive = false)
    private BigDecimal limitDownPrice;

    /** 买入方向状态说明。 */
    @Size(max = 512)
    private String buyReason;

    /** 卖出方向状态说明。 */
    @Size(max = 512)
    private String sellReason;

    /** 数据源事实时间。 */
    @NotNull
    private LocalDateTime capturedAt;
}
// AI_GENERATE_END-----
