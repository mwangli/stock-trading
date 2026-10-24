// AI_GENERATE_START----
package com.stock.market.dto;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 单根 5 分钟 K 线。
 *
 * @author mwangli
 * @since 2026-09-25
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MinuteBarImportItemDto {

    /** 股票代码。 */
    @NotBlank
    private String stockCode;

    /** K 线起始时间或结束时间，由数据源版本统一定义。 */
    @NotNull
    private LocalDateTime barTime;

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

    /** 成交量。 */
    @NotNull
    @DecimalMin("0")
    private BigDecimal volume;

    /** 成交额。 */
    @NotNull
    @DecimalMin("0")
    private BigDecimal amount;
}
// AI_GENERATE_END----
