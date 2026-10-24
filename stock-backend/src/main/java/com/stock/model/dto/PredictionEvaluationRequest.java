// AI_GENERATE_START----
package com.stock.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 刷新实际结果请求。
 *
 * @author mwangli
 * @since 2026-10-12
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PredictionEvaluationRequest {

    /** 预测批次 ID；为空时评估最新批次。 */
    private String batchId;
}
// AI_GENERATE_END----
