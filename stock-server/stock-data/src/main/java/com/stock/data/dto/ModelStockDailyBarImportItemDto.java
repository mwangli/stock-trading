// AI_GENERATE_START------
package com.stock.data.dto;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 单只股票单日模型特征行情。
 *
 * @author mwangli
 * @since 2026-09-25
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ModelStockDailyBarImportItemDto {

    /** 股票代码。 */
    @NotBlank
    private String stockCode;

    /** 交易日期。 */
    @NotNull
    private LocalDate tradeDate;

    /** 开盘价。 */
    @NotNull
    @DecimalMin(value = "0", inclusive = false)
    private BigDecimal openPrice;

    /** 最高价。 */
    @NotNull
    @DecimalMin(value = "0", inclusive = false)
    private BigDecimal highPrice;

    /** 最低价。 */
    @NotNull
    @DecimalMin(value = "0", inclusive = false)
    private BigDecimal lowPrice;

    /** 收盘价。 */
    @NotNull
    @DecimalMin(value = "0", inclusive = false)
    private BigDecimal closePrice;

    /** 昨日收盘参考价；用于免费日线连续归一化，首个可用交易日允许为空。 */
    @DecimalMin(value = "0", inclusive = false)
    private BigDecimal preClosePrice;

    /** 成交量。 */
    @NotNull
    @DecimalMin("0")
    private BigDecimal volume;

    /** 成交额。 */
    @NotNull
    @DecimalMin("0")
    private BigDecimal amount;

    /** 当日换手率；Tushare 免费 daily 不提供时允许为空。 */
    @DecimalMin("0")
    private BigDecimal turnoverRate;
}
// AI_GENERATE_END------
