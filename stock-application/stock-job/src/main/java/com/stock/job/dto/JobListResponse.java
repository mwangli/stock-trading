// AI_GENERATE_START---
package com.stock.job.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

/**
 * 任务列表响应。
 *
 * @author mwangli
 * @since 2026-10-11
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JobListResponse {

    /** 任务列表。 */
    private List<JobItemResponse> items;
}
// AI_GENERATE_END---
