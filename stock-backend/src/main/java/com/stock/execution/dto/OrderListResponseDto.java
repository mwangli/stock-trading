// AI_GENERATE_START-------
package com.stock.execution.dto;

import com.stock.execution.vo.OrderResult;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 当日订单分页业务数据。
 *
 * @author mwangli
 * @since 2026-10-23
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderListResponseDto {

    /** 当前页订单列表。 */
    private List<OrderResult> items;

    /** 当日订单总数。 */
    private long total;

    /** 当前页码，从 1 开始。 */
    private int current;

    /** 每页记录数。 */
    private int pageSize;
}
// AI_GENERATE_END-------
