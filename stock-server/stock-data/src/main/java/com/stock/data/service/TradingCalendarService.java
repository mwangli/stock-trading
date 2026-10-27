// AI_GENERATE_START--------------
package com.stock.data.service;

import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 股票市场工作日计算服务。
 *
 * 系统统一将周一至周五视为交易日，不维护节假日日历，也不访问数据库或外部数据源。
 * 节假日工作日任务允许正常触发，由行情和交易执行结果决定是否存在可处理数据。
 *
 * @author mwangli
 * @since 2026-10-13
 */
@Service
public class TradingCalendarService {

    /**
     * 判断指定日期是否为周一至周五。
     *
     * @param date 自然日期
     * @return 周一至周五返回 true，周末或空值返回 false
     */
    public boolean isTradingDay(LocalDate date) {
        return date != null && date.getDayOfWeek() != DayOfWeek.SATURDAY
                && date.getDayOfWeek() != DayOfWeek.SUNDAY;
    }

    /**
     * 查询日期范围内的周一至周五。
     *
     * @param startDate 起始日期
     * @param endDate 结束日期
     * @return 按日期升序排列的工作日
     */
    public List<LocalDate> tradingDaysBetween(LocalDate startDate, LocalDate endDate) {
        validateDateRange(startDate, endDate);
        List<LocalDate> tradingDays = new ArrayList<>();
        for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
            if (isTradingDay(date)) {
                tradingDays.add(date);
            }
        }
        return List.copyOf(tradingDays);
    }

    /**
     * 要求指定日期必须为周一至周五。
     *
     * @param date 自然日期
     * @throws IllegalStateException 日期为空或位于周末时抛出
     */
    public void requireTradingDay(LocalDate date) {
        if (!isTradingDay(date)) {
            throw new IllegalStateException("指定日期不是工作日: " + date);
        }
    }

    /**
     * 解析指定日期之后的首个周一至周五。
     *
     * @param date 基准日期
     * @return 下一工作日
     */
    public LocalDate nextTradingDay(LocalDate date) {
        if (date == null) {
            throw new IllegalArgumentException("基准日期不能为空");
        }
        LocalDate candidate = date.plusDays(1);
        while (!isTradingDay(candidate)) {
            candidate = candidate.plusDays(1);
        }
        return candidate;
    }

    /**
     * 尝试解析指定日期之后的首个周一至周五。
     *
     * @param date 基准日期
     * @return 日期非空时返回下一工作日，否则返回空
     */
    public Optional<LocalDate> findNextTradingDay(LocalDate date) {
        return date == null ? Optional.empty() : Optional.of(nextTradingDay(date));
    }

    /**
     * 解析指定日期之前最近的周一至周五。
     *
     * @param date 基准日期
     * @return 前一工作日
     */
    public LocalDate previousTradingDay(LocalDate date) {
        if (date == null) {
            throw new IllegalArgumentException("基准日期不能为空");
        }
        LocalDate candidate = date.minusDays(1);
        while (!isTradingDay(candidate)) {
            candidate = candidate.minusDays(1);
        }
        return candidate;
    }

    private void validateDateRange(LocalDate startDate, LocalDate endDate) {
        if (startDate == null || endDate == null || startDate.isAfter(endDate)) {
            throw new IllegalArgumentException("工作日查询范围无效");
        }
    }
}
// AI_GENERATE_END--------------
