package com.stock.dataCollector.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 新闻采集接口响应 DTO
 *
 * @author mwangli
 * @since 2026-03-14
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NewsCollectResponseDto {

    /**
     * 是否执行成功
     */
    private boolean success;

    /**
     * 提示信息
     */
    private String message;

    /**
     * 处理的股票数量
     */
    private Integer processedCount;

    /**
     * 新增新闻条数
     */
    private Integer savedCount;

    /**
     * 失败的股票数量
     */
    private Integer failedCount;
}
