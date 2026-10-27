// AI_GENERATE_START ---
package com.stock.agent.model;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 券商历史委托或成交只读事实。
 *
 * @param type 查询类型
 * @param startDate 开始日期，包含当天
 * @param endDate 结束日期，包含当天
 * @param total 记录总数
 * @param items 有限脱敏委托或成交摘要
 * @param asOf 查询时间
 * @author mwangli
 * @since 2026-10-25
 */
public record BrokerHistoryFact(
        String type, LocalDate startDate, LocalDate endDate, int total,
        List<String> items, LocalDateTime asOf) {
}
// AI_GENERATE_END ---