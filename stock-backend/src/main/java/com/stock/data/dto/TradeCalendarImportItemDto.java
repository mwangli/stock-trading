// AI_GENERATE_START---
package com.stock.data.dto;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDate;

/**
 * 单日交易日历记录。
 *
 * @author mwangli
 * @since 2026-09-25
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TradeCalendarImportItemDto {

    /** 市场代码。 */
    @NotBlank
    private String market;

    /** 自然日期。 */
    @NotNull
    private LocalDate tradeDate;

    /** 是否为交易日。 */
    private boolean tradingDay;

    /** 前一交易日。 */
    private LocalDate previousTradingDay;

    /** 后一交易日。 */
    private LocalDate nextTradingDay;
}
// AI_GENERATE_END---
