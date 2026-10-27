// AI_GENERATE_START-------
package com.stock.data.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 股票 K 线业务数据。
 *
 * @author mwangli
 * @since 2026-10-23
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KlineDataResponseDto {

    /** K 线图表载荷。 */
    private KlineDataPayloadDto payload;

    /** 返回的有效 K 线记录数量。 */
    private int total;
}
// AI_GENERATE_END-------
