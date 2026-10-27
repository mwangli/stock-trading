// AI_GENERATE_START-------
package com.stock.data.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 股票数据完整性校验业务数据。
 *
 * @author mwangli
 * @since 2026-10-23
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockDataValidationResponseDto {

    /** 参与校验的总记录数。 */
    private int totalRecords;

    /** 按字段统计的缺失记录数。 */
    private Map<String, Integer> validation;

    /** 股票代码是否全部有效。 */
    private boolean valid;

    /** 以股票名称完整率表示的数据质量。 */
    private String dataQuality;
}
// AI_GENERATE_END-------
