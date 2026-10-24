// AI_GENERATE_START----
package com.stock.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

/**
 * 预测明细分页响应。
 *
 * @author mwangli
 * @since 2026-10-12
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PredictionResultPageResponse {

    /** 当前页预测明细。 */
    private List<PredictionResultResponse> items;

    /** 总记录数。 */
    private long total;

    /** 当前页码。 */
    private int current;

    /** 每页大小。 */
    private int pageSize;
}
// AI_GENERATE_END----
