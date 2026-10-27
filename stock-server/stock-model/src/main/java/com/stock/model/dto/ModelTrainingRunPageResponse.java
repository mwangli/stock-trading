// AI_GENERATE_START----
package com.stock.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

/**
 * 训练运行分页响应。
 *
 * @author mwangli
 * @since 2026-10-10
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ModelTrainingRunPageResponse {

    /** 当前页训练运行数据。 */
    private List<ModelTrainingRunResponse> items;

    /** 总记录数。 */
    private long total;

    /** 当前页码，从 1 开始。 */
    private int current;

    /** 每页大小。 */
    private int pageSize;
}
// AI_GENERATE_END----
