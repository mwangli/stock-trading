package com.stock.dataCollector.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 涨幅榜单项 DTO
 *
 * 对应 /api/stockInfo/listIncreaseRate 接口中单只股票的展示信息。
 *
 * @author AI Assistant
 * @since 1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TopIncreaseItemDto {

    /**
     * 股票代码
     */
    private String code;

    /**
     * 股票名称
     */
    private String name;

    /**
     * 涨跌幅
     */
    private BigDecimal changePercent;
}

