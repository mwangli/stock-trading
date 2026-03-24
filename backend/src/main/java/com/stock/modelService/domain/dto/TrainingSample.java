package com.stock.modelService.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 训练样本
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class TrainingSample {

    private String text;
    private Integer label;
    private String source;
}
