// AI_GENERATE_START---
package com.stock.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

/**
 * 模型版本分页响应。
 *
 * @author mwangli
 * @since 2026-10-10
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ModelVersionPageResponse {

    /** 当前页版本数据。 */
    private List<ModelVersionResponse> items;

    /** 总记录数。 */
    private long total;

    /** 当前页码，从 1 开始。 */
    private int current;

    /** 每页大小。 */
    private int pageSize;
}
// AI_GENERATE_END---
