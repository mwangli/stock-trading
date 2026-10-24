// AI_GENERATE_START--------
package com.stock.market.dto;

import com.stock.market.entity.ReferenceSeriesType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

/**
 * 市场或行业参考日线批量导入请求。
 *
 * @author mwangli
 * @since 2026-09-25
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ModelReferenceDailyBarImportRequestDto {

    /** 数据来源。 */
    @NotBlank
    private String source;

    /** 不可覆盖的数据版本。 */
    @NotBlank
    private String sourceVersion;

    /** 本批参考序列类型。 */
    @NotNull
    private ReferenceSeriesType seriesType;

    /** 参考日线列表。 */
    @Valid
    @NotEmpty
    @Size(max = 20000)
    private List<ModelReferenceDailyBarImportItemDto> items;

}
// AI_GENERATE_END--------
