// AI_GENERATE_START--------
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
 * 交易日历批量导入请求。
 *
 * @author mwangli
 * @since 2026-09-25
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TradeCalendarImportRequestDto {

    /** 数据来源。 */
    @NotBlank
    private String source;

    /** 数据源版本或批次。 */
    @NotBlank
    private String sourceVersion;

    /** 交易日历记录列表。 */
    @Valid
    @NotEmpty
    @Size(max = 5000)
    private List<TradeCalendarImportItemDto> items;

}
// AI_GENERATE_END--------
