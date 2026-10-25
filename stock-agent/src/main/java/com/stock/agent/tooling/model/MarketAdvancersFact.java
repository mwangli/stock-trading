// AI_GENERATE_START -
package com.stock.agent.tooling.model;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 最近可用行情日的涨跌分布事实。
 *
 * @param dataDate 行情数据日期
 * @param asOf 最近行情更新时间
 * @param totalCount 有效股票总数
 * @param upCount 上涨股票数量
 * @param downCount 下跌股票数量
 * @param flatCount 平盘股票数量
 * @param items 涨幅前二十名
 * @param availabilityReason 数据不可用原因
 * @author mwangli
 * @since 2026-10-25
 */
public record MarketAdvancersFact(
        LocalDate dataDate, LocalDateTime asOf, int totalCount, int upCount, int downCount,
        int flatCount, List<MarketAdvancerItemFact> items, String availabilityReason) {
}
// AI_GENERATE_END -
