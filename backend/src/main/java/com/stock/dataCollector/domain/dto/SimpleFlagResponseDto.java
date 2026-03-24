package com.stock.dataCollector.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 简单布尔结果响应 DTO
 *
 * 适用于自选股添加/取消等仅返回 success 标记的接口。
 *
 * @author AI Assistant
 * @since 1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SimpleFlagResponseDto {

    /**
     * 是否成功
     */
    private boolean success;
}

