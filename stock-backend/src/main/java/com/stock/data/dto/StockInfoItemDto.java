// AI_GENERATE_START---
package com.stock.data.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 股票基础信息响应项。
 *
 * @author mwangli
 * @since 2026-10-23
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockInfoItemDto {

    /** 股票记录标识，可为空。 */
    private String id;

    /** 股票代码。 */
    private String code;

    /** 股票名称。 */
    private String name;

    /** 所属市场，如 SH、SZ、BJ。 */
    private String market;

    /** 最新价格，单位为元。 */
    private BigDecimal price;

    /** 最新涨跌额，单位为元。 */
    private BigDecimal changeAmount;

    /** 最新涨跌幅，单位为百分比。 */
    private BigDecimal changePercent;

    /** 当日成交量，单位沿用数据源定义。 */
    private BigDecimal volume;

    /** 总市值，单位沿用数据源定义。 */
    private BigDecimal totalMarketValue;

    /** 换手率，单位为百分比。 */
    private BigDecimal turnoverRate;

    /** 量比。 */
    private BigDecimal volumeRatio;

    /** 证券类别编码。 */
    private Integer industryCode;

    /** 上市日期。 */
    private LocalDate listedDate;

    /** 数据更新时间。 */
    private LocalDateTime updateTime;
}
// AI_GENERATE_END---
