// AI_GENERATE_START---
package com.stock.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 历史预测重算请求。
 *
 * @author mwangli
 * @since 2026-10-18
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PredictionRerunRequest {

    /** 原历史预测批次 ID。 */
    private String batchId;

    /** 发起重算的操作人名称。 */
    private String operatorName;
}
// AI_GENERATE_END---
