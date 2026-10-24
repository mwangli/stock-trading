// AI_GENERATE_START-------
package com.stock.market.dto;

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
 * 模型特征专用股票日线批量导入请求。
 *
 * @author mwangli
 * @since 2026-09-25
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ModelStockDailyBarImportRequestDto {

    /** 数据来源。 */
    @NotBlank
    private String source;

    /** 不可覆盖的数据版本。 */
    @NotBlank
    private String sourceVersion;

    /** 成交量单位，例如 SHARE。 */
    @NotBlank
    private String volumeUnit;

    /** 成交额单位，例如 CNY。 */
    @NotBlank
    private String amountUnit;

    /** 股票日线列表。 */
    @Valid
    @NotEmpty
    @Size(max = 20000)
    private List<ModelStockDailyBarImportItemDto> items;

}
// AI_GENERATE_END-------
