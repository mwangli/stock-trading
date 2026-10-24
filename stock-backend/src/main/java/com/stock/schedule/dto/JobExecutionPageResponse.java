// AI_GENERATE_START----
package com.stock.schedule.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

/**
 * 执行记录分页响应。
 *
 * @author mwangli
 * @since 2026-10-11
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JobExecutionPageResponse {

    /** 当前页数据。 */
    private List<JobExecutionResponse> items;

    /** 总记录数。 */
    private long total;

    /** 当前页码，从 1 开始。 */
    private int current;

    /** 每页大小。 */
    private int pageSize;
}
// AI_GENERATE_END----
