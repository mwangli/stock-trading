// AI_GENERATE_START- -
package com.stock.data.dto;

import lombok.Builder;
import lombok.Value;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 最近可用行情日的涨跌分布和涨幅榜。
 *
 * @author mwangli
 * @since 2026-10-25
 */
@Value
@Builder
public class MarketAdvancersDto {

    /** 行情数据对应日期，可为空。 */
    LocalDate dataDate;

    /** 最近行情更新时间，可为空。 */
    LocalDateTime asOf;

    /** 当日有效股票总数。 */
    int totalCount;

    /** 上涨股票数量。 */
    int upCount;

    /** 下跌股票数量。 */
    int downCount;

    /** 平盘股票数量。 */
    int flatCount;

    /** 按涨跌幅降序排列的上涨股票，最多二十项。 */
    List<MarketAdvancerItemDto> items;

    /** 数据为空时的原因，可为空。 */
    String availabilityReason;
}
// AI_GENERATE_END- -
