// AI_GENERATE_START------
package com.stock.data.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 市场统计信息 VO，用于前端市场概览页面的统计展示
 *
 * @author mwangli
 * @since 2026-10-23
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MarketStatsDto {

    /**
     * 市场交易状态，如交易中、已收盘或休市。
     */
    private String marketStatus;
    /**
     * 市场综合涨跌幅，单位为百分比。
     */
    private BigDecimal changePercent;
    /**
     * 上涨股票数量，单位为只。
     */
    private Integer upCount;
    /**
     * 下跌股票数量，单位为只。
     */
    private Integer downCount;
    /**
     * 平盘股票数量，单位为只。
     */
    private Integer flatCount;
    /**
     * 市场总成交额，单位为元。
     */
    private BigDecimal totalAmount;
    /**
     * 市场总成交量，单位为股。
     */
    private BigDecimal totalVolume;
    /**
     * 涨幅最高股票代码。
     */
    private String topGainerCode;
    /**
     * 涨幅最高股票名称。
     */
    private String topGainerName;
    /**
     * 涨幅最高股票的涨跌幅，单位为百分比。
     */
    private BigDecimal topGainerChange;
    /**
     * 跌幅最高股票代码。
     */
    private String topLoserCode;
    /**
     * 跌幅最高股票名称。
     */
    private String topLoserName;
    /**
     * 跌幅最高股票的涨跌幅，单位为百分比。
     */
    private BigDecimal topLoserChange;
    /**
     * 参与统计的股票总数，单位为只。
     */
    private Integer totalCount;
    /**
     * 市场平均换手率，单位为百分比。
     */
    private BigDecimal avgTurnoverRate;
}

// AI_GENERATE_END------
