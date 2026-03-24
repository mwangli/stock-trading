package com.stock.modelService.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * LSTM 模型列表项 DTO
 * 用于列表接口返回，不包含模型参数二进制
 *
 * @author AI Assistant
 * @since 1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LstmModelListItemDto {

    /**
     * 文档 ID（MongoDB _id）
     */
    private String id;

    /**
     * 模型标识，通常为股票代码或逗号分隔的多只股票代码
     */
    private String modelName;

    /**
     * 展示名称，为首只股票名称或 modelName 回退
     */
    private String name;

    /**
     * 训练保存时的 epoch
     */
    private int epoch;

    /**
     * 保存时间
     */
    private LocalDateTime createdAt;

    /**
     * 模型版本，如 v1
     */
    private String modelVersion;

    /**
     * 训练集损失（可选，内部用于排序与推导分数，前端不展示）
     */
    private Double trainLoss;

    /**
     * 验证集损失（可选，用于排序与推导分数，前端不展示）
     */
    private Double valLoss;

    /**
     * 收益金额（元），暂无实盘数据时为 null
     */
    private Double profitAmount;

    /**
     * 效果分数（0～100），由验证集表现推导，无数据时为 null
     */
    private Double score;
}
