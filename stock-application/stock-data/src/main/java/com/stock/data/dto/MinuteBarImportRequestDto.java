// AI_GENERATE_START------
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
 * 股票 5 分钟 K 线批量导入请求。
 *
 * @author mwangli
 * @since 2026-09-25
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MinuteBarImportRequestDto {

    /** 数据来源。 */
    @NotBlank
    private String source;

    /** 数据源版本或批次。 */
    @NotBlank
    private String sourceVersion;

    /** 成交量单位，例如 SHARE、LOT。 */
    @NotBlank
    private String volumeUnit;

    /** 成交额单位，例如 CNY。 */
    @NotBlank
    private String amountUnit;

    /** 5 分钟 K 线记录列表。 */
    @Valid
    @NotEmpty
    @Size(max = 20000)
    private List<MinuteBarImportItemDto> items;

}
// AI_GENERATE_END------
