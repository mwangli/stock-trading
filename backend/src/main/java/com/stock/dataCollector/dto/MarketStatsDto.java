package com.stock.dataCollector.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 市场统计信息DTO
 * 用于前端市场概览页面的统计展示
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MarketStatsDto {

    /**
     * 市场状态 (开盘/休市)
     */
    private String marketStatus;

    /**
     * 涨跌幅 (大盘涨幅)
     */
    private BigDecimal changePercent;

    /**
     * 上涨家数
     */
    private Integer upCount;

    /**
     * 下跌家数
     */
    private Integer downCount;

    /**
     * 平盘家数
     */
    private Integer flatCount;

    /**
     * 总成交额 (单位: 元)
     */
    private BigDecimal totalAmount;

    /**
     * 总成交量 (单位: 手)
     */
    private BigDecimal totalVolume;

    /**
     * 领涨股票代码
     */
    private String topGainerCode;

    /**
     * 领涨股票名称
     */
    private String topGainerName;

    /**
     * 领涨股票涨幅
     */
    private BigDecimal topGainerChange;

    /**
     * 领跌股票代码
     */
    private String topLoserCode;

    /**
     * 领跌股票名称
     */
    private String topLoserName;

    /**
     * 领跌股票跌幅
     */
    private BigDecimal topLoserChange;

    /**
     * 股票总数
     */
    private Integer totalCount;

    /**
     * 换手率
     */
    private BigDecimal avgTurnoverRate;
}
