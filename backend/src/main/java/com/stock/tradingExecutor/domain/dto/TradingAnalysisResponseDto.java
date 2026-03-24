package com.stock.tradingExecutor.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 交易分析结果响应 DTO
 *
 * 目前保持 data 为通用结构，后续如有明确字段可再细化为专门的 DTO。
 *
 * @author AI Assistant
 * @since 1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TradingAnalysisResponseDto {

    /**
     * 分析数据列表（占位，当前为 Mock）
     */
    private List<Map<String, Object>> data;

    /**
     * 是否成功
     */
    private boolean success;
}

