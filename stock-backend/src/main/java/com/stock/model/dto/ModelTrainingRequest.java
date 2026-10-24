// AI_GENERATE_START-------
package com.stock.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 启动模型训练请求。
 *
 * @author mwangli
 * @since 2026-10-10
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ModelTrainingRequest {

    /** 每只股票读取的交易日数量。 */
    private Integer days;

    /** 训练轮次覆盖值。 */
    private Integer epochs;

    /** 批次大小覆盖值。 */
    private Integer batchSize;

    /** 学习率覆盖值。 */
    private Double learningRate;

    /** 候选模型架构：RANKING_LSTM、DUAL_ATTENTION_RANKING_LSTM 或 MASTER_TRANSFORMER。 */
    private String architectureType;

    /** 操作人名称。 */
    private String operatorName;

}
// AI_GENERATE_END-------
