// AI_GENERATE_START-------
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
 * 股票单日开盘可成交性事实批量导入请求。
 *
 * @author mwangli
 * @since 2026-09-25
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockDailyTradabilityImportRequestDto {

    /** 可成交性数据来源。 */
    @NotBlank
    private String source;

    /** 不可与其他版本相互覆盖的数据版本。 */
    @NotBlank
    private String sourceVersion;

    /** 数据源事实列表。 */
    @Valid
    @NotEmpty
    @Size(max = 20000)
    private List<StockDailyTradabilityImportItemDto> items;

}
// AI_GENERATE_END-------
