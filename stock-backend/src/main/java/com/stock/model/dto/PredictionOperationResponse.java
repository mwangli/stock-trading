// AI_GENERATE_START----
package com.stock.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 预测操作响应。
 *
 * @author mwangli
 * @since 2026-10-12
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PredictionOperationResponse {

    /** 预测批次 ID。 */
    private String batchId;

    /** 当前批次状态。 */
    private String status;

    /** 操作结果说明。 */
    private String message;
}
// AI_GENERATE_END----
