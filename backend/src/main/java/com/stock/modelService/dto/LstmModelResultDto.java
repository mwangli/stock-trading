package com.stock.modelService.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * LSTM 模型查询结果 DTO
 * 用于「查询结果」接口，展示收益金额与分数（不展示损失）
 *
 * @author AI Assistant
 * @since 1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LstmModelResultDto {

    /**
     * 模型文档 ID
     */
    private String modelId;

    /**
     * 模型名称（如股票代码）
     */
    private String modelName;

    /**
     * 收益金额（元），暂无实盘数据时为 null
     */
    private Double profitAmount;

    /**
     * 效果分数（0～100，可由验证集表现推导），无数据时为 null
     */
    private Double score;
}
