package com.stock.dataCollector.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 市场统计信息 VO，用于前端市场概览页面的统计展示
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MarketStatsDto {

    private String marketStatus;
    private BigDecimal changePercent;
    private Integer upCount;
    private Integer downCount;
    private Integer flatCount;
    private BigDecimal totalAmount;
    private BigDecimal totalVolume;
    private String topGainerCode;
    private String topGainerName;
    private BigDecimal topGainerChange;
    private String topLoserCode;
    private String topLoserName;
    private BigDecimal topLoserChange;
    private Integer totalCount;
    private BigDecimal avgTurnoverRate;
}
