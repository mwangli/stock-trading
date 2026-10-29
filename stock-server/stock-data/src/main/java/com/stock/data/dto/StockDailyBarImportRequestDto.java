// AI_GENERATE_START -
package com.stock.data.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 统一股票日线批量导入请求。
 *
 * @author mwangli
 * @since 2026-10-29
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockDailyBarImportRequestDto {

    /** 行情数据来源。 */
    @NotBlank
    private String source;

    /** 独立于模型版本的数据契约版本。 */
    @NotBlank
    private String sourceVersion;

    /** 成交量单位，例如 LOT。 */
    @NotBlank
    private String volumeUnit;

    /** 成交额单位，例如 CNY_THOUSAND。 */
    @NotBlank
    private String amountUnit;

    /** 待导入的股票日线。 */
    @Valid
    @NotEmpty
    @Size(max = 20000)
    private List<StockDailyBarImportItemDto> items;
}
// AI_GENERATE_END -
