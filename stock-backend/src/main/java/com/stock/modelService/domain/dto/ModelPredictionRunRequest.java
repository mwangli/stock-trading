// AI_GENERATE_START -
package com.stock.modelService.domain.dto;

import lombok.Data;

import java.time.LocalDate;

/**
 * 全市场模型预测执行请求。
 * 支持按指定目标交易日执行预测；目标日期为空时沿用最新完整日线推导逻辑。
 *
 * @author mwangli
 * @since 2026-10-20
 */
@Data
public class ModelPredictionRunRequest {

    /** 操作人名称，可为空，空值使用系统默认操作人。 */
    private String operatorName;

    /** 预测目标交易日，格式 yyyy-MM-dd，可为空且不得晚于当前自然日。 */
    private LocalDate targetTradeDate;
}
// AI_GENERATE_END -
