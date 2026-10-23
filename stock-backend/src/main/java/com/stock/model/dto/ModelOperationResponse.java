// AI_GENERATE_START---
package com.stock.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 模型操作响应。
 *
 * @author mwangli
 * @since 2026-10-10
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ModelOperationResponse {

    /** 操作类型。 */
    private String operationType;

    /** 训练运行或模型版本 ID。 */
    private String operationId;

    /** 操作结果说明。 */
    private String message;
}
// AI_GENERATE_END---
