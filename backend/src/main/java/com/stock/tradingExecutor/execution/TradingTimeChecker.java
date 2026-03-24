package com.stock.tradingExecutor.execution;

import com.stock.tradingExecutor.config.TradingTimeConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * 交易时间检查器
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TradingTimeChecker {

    private final TradingTimeConfig config;

    /**
     * 是否在交易时段
     */
    public boolean isTradingTime() {
        return isTradingTime(LocalDateTime.now());
    }

    public boolean isTradingTime(LocalDateTime dateTime) {
        if (!isWeekday(dateTime.toLocalDate())) {
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
     * 是否临近收盘 (14:55后)
     */
    public boolean isNearClose() {
        return isNearClose(LocalDateTime.now());
    }

    public boolean isNearClose(LocalDateTime dateTime) {
        if (!isTradingTime(dateTime)) {
            return false;
        }
        LocalTime time = dateTime.toLocalTime();
        return !time.isBefore(config.getNearCloseTime());
    }

    public LocalTime getBuyDeadLine() {
        return config.getBuyDeadLine();
    }

    public LocalTime getSellDeadLine() {
        return config.getSellDeadLine();
    }

    public boolean isPastBuyDeadLine() {
        return isPastBuyDeadLine(LocalDateTime.now());
    }

    public boolean isPastBuyDeadLine(LocalDateTime dateTime) {
        LocalTime time = dateTime.toLocalTime();
        return time.isAfter(config.getBuyDeadLine());
    }

    public boolean isPastSellDeadLine() {
        return isPastSellDeadLine(LocalDateTime.now());
    }

    public boolean isPastSellDeadLine(LocalDateTime dateTime) {
        LocalTime time = dateTime.toLocalTime();
        return time.isAfter(config.getSellDeadLine());
    }

    public boolean isWeekday(LocalDate date) {
        DayOfWeek dayOfWeek = date.getDayOfWeek();
        return dayOfWeek != DayOfWeek.SATURDAY && dayOfWeek != DayOfWeek.SUNDAY;
    }

    public boolean isWeekday() {
        return isWeekday(LocalDate.now());
    }

    public String getTradingSession() {
        return getTradingSession(LocalDateTime.now());
    }

    public String getTradingSession(LocalDateTime dateTime) {
        if (!isWeekday(dateTime.toLocalDate())) {
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
