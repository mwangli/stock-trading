// AI_GENERATE_START------
package com.stock.trading.execution;

import com.stock.data.service.TradingCalendarService;
import com.stock.trading.config.TradingTimeConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * 交易时间检查器。
 * 交易日期由已确认的交易日历决定，避免法定休市日被普通工作日规则误判。
 *
 * @author mwangli
 * @since 2026-10-13
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TradingTimeChecker {

    private final TradingTimeConfig config;
    private final TradingCalendarService tradingCalendarService;

    /**
     * 判断当前时间是否处于交易时段。
     *
     * @return 是否处于交易时段
     */
    public boolean isTradingTime() {
        return isTradingTime(LocalDateTime.now());
    }

    /**
     * 判断指定时间是否处于交易时段。
     *
     * @param dateTime 待判断时间
     * @return 是否处于交易时段
     */
    public boolean isTradingTime(LocalDateTime dateTime) {
        if (!isTradingDay(dateTime.toLocalDate())) {
            return false;
        }

        LocalTime time = dateTime.toLocalTime();
        boolean morningSession = !time.isBefore(config.getMorningStart())
                && !time.isAfter(config.getMorningEnd());
        boolean afternoonSession = !time.isBefore(config.getAfternoonStart())
                && !time.isAfter(config.getAfternoonEnd());

        return morningSession || afternoonSession;
    }

    /**
     * 判断当前时间是否临近收盘。
     *
     * @return 是否临近收盘
     */
    public boolean isNearClose() {
        return isNearClose(LocalDateTime.now());
    }

    /**
     * 判断指定时间是否临近收盘。
     *
     * @param dateTime 待判断时间
     * @return 是否临近收盘
     */
    public boolean isNearClose(LocalDateTime dateTime) {
        if (!isTradingTime(dateTime)) {
            return false;
        }
        LocalTime time = dateTime.toLocalTime();
        return !time.isBefore(config.getNearCloseTime());
    }

    /**
     * 获取买入截止时间。
     *
     * @return 买入截止时间
     */
    public LocalTime getBuyDeadLine() {
        return config.getBuyDeadLine();
    }

    /**
     * 获取卖出截止时间。
     *
     * @return 卖出截止时间
     */
    public LocalTime getSellDeadLine() {
        return config.getSellDeadLine();
    }

    /**
     * 判断当前时间是否超过买入截止时间。
     *
     * @return 是否超过截止时间
     */
    public boolean isPastBuyDeadLine() {
        return isPastBuyDeadLine(LocalDateTime.now());
    }

    /**
     * 判断指定时间是否超过买入截止时间。
     *
     * @param dateTime 待判断时间
     * @return 是否超过截止时间
     */
    public boolean isPastBuyDeadLine(LocalDateTime dateTime) {
        LocalTime time = dateTime.toLocalTime();
        return time.isAfter(config.getBuyDeadLine());
    }

    /**
     * 判断当前时间是否超过卖出截止时间。
     *
     * @return 是否超过截止时间
     */
    public boolean isPastSellDeadLine() {
        return isPastSellDeadLine(LocalDateTime.now());
    }

    /**
     * 判断指定时间是否超过卖出截止时间。
     *
     * @param dateTime 待判断时间
     * @return 是否超过截止时间
     */
    public boolean isPastSellDeadLine(LocalDateTime dateTime) {
        LocalTime time = dateTime.toLocalTime();
        return time.isAfter(config.getSellDeadLine());
    }

    /**
     * 判断日期是否为已确认交易日。
     *
     * @param date 自然日期
     * @return 是否为交易日
     */
    public boolean isTradingDay(LocalDate date) {
        return tradingCalendarService.isTradingDay(date);
    }

    /**
     * 判断今天是否为已确认交易日。
     *
     * @return 是否为交易日
     */
    public boolean isTradingDay() {
        return isTradingDay(LocalDate.now());
    }


    /**
     * 获取当前交易时段名称。
     *
     * @return 交易时段名称
     */
    public String getTradingSession() {
        return getTradingSession(LocalDateTime.now());
    }

    /**
     * 获取指定时间对应的交易时段名称。
     *
     * @param dateTime 待判断时间
     * @return 交易时段名称
     */
    public String getTradingSession(LocalDateTime dateTime) {
        if (!isTradingDay(dateTime.toLocalDate())) {
            return "非交易日";
        }

        LocalTime time = dateTime.toLocalTime();

        if (time.isBefore(config.getMorningStart())) {
            return "盘前";
        } else if (!time.isBefore(config.getMorningStart()) && time.isBefore(config.getMorningEnd())) {
            return "早盘";
        } else if (time.isBefore(config.getAfternoonStart())) {
            return "午休";
        } else if (!time.isBefore(config.getAfternoonStart()) && time.isBefore(config.getAfternoonEnd())) {
            return "午盘";
        } else {
            return "收盘";
        }
    }
}
// AI_GENERATE_END------
