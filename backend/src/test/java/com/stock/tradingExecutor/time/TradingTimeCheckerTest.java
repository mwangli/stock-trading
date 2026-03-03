package com.stock.tradingExecutor.time;

import com.stock.tradingExecutor.config.TradingTimeConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 交易时间检查器测试
 */
class TradingTimeCheckerTest {
    
    private TradingTimeChecker tradingTimeChecker;
    private TradingTimeConfig config;
    
    @BeforeEach
    void setUp() {
        config = new TradingTimeConfig();
        config.setMorningStart(LocalTime.of(9, 30));
        config.setMorningEnd(LocalTime.of(11, 30));
        config.setAfternoonStart(LocalTime.of(13, 0));
        config.setAfternoonEnd(LocalTime.of(15, 0));
        config.setBuyDeadLine(LocalTime.of(14, 50));
        config.setSellDeadLine(LocalTime.of(14, 57));
        config.setNearCloseTime(LocalTime.of(14, 55));
        
        tradingTimeChecker = new TradingTimeChecker(config);
    }
    
    @Test
    void testIsTradingTime_MorningSession() {
        // 早盘时间 (周一 10:00)
        LocalDateTime dateTime = LocalDateTime.of(2024, 1, 15, 10, 0);
        assertTrue(tradingTimeChecker.isTradingTime(dateTime));
    }
    
    @Test
    void testIsTradingTime_AfternoonSession() {
        // 午盘时间 (周一 14:00)
        LocalDateTime dateTime = LocalDateTime.of(2024, 1, 15, 14, 0);
        assertTrue(tradingTimeChecker.isTradingTime(dateTime));
    }
    
    @Test
    void testIsTradingTime_BeforeOpen() {
        // 开盘前 (周一 9:00)
        LocalDateTime dateTime = LocalDateTime.of(2024, 1, 15, 9, 0);
        assertFalse(tradingTimeChecker.isTradingTime(dateTime));
    }
    
    @Test
    void testIsTradingTime_AfterClose() {
        // 收盘后 (周一 15:30)
        LocalDateTime dateTime = LocalDateTime.of(2024, 1, 15, 15, 30);
        assertFalse(tradingTimeChecker.isTradingTime(dateTime));
    }
    
    @Test
    void testIsTradingTime_Weekend() {
        // 周末
        LocalDateTime saturday = LocalDateTime.of(2024, 1, 20, 10, 0);
        LocalDateTime sunday = LocalDateTime.of(2024, 1, 21, 10, 0);
        
        assertFalse(tradingTimeChecker.isTradingTime(saturday));
        assertFalse(tradingTimeChecker.isTradingTime(sunday));
    }
    
    @Test
    void testIsNearClose_AfterNearCloseTime() {
        // 临近收盘 (周一 14:55)
        LocalDateTime dateTime = LocalDateTime.of(2024, 1, 15, 14, 55);
        assertTrue(tradingTimeChecker.isNearClose(dateTime));
    }
    
    @Test
    void testIsNearClose_BeforeNearCloseTime() {
        // 非临近收盘 (周一 14:00)
        LocalDateTime dateTime = LocalDateTime.of(2024, 1, 15, 14, 0);
        assertFalse(tradingTimeChecker.isNearClose(dateTime));
    }
    
    @Test
    void testIsPastBuyDeadLine() {
        // 已过买入截止时间 (周一 14:51)
        LocalDateTime dateTime = LocalDateTime.of(2024, 1, 15, 14, 51);
        assertTrue(tradingTimeChecker.isPastBuyDeadLine(dateTime));
        
        // 未过买入截止时间 (周一 14:40)
        LocalDateTime beforeTime = LocalDateTime.of(2024, 1, 15, 14, 40);
        assertFalse(tradingTimeChecker.isPastBuyDeadLine(beforeTime));
    }
    
    @Test
    void testIsPastSellDeadLine() {
        // 已过卖出截止时间 (周一 14:58)
        LocalDateTime dateTime = LocalDateTime.of(2024, 1, 15, 14, 58);
        assertTrue(tradingTimeChecker.isPastSellDeadLine(dateTime));
        
        // 未过卖出截止时间 (周一 14:50)
        LocalDateTime beforeTime = LocalDateTime.of(2024, 1, 15, 14, 50);
        assertFalse(tradingTimeChecker.isPastSellDeadLine(beforeTime));
    }
    
    @Test
    void testIsWeekday() {
        // 周一到周五
        assertTrue(tradingTimeChecker.isWeekday(LocalDate.of(2024, 1, 15))); // 周一
        assertTrue(tradingTimeChecker.isWeekday(LocalDate.of(2024, 1, 16))); // 周二
        assertTrue(tradingTimeChecker.isWeekday(LocalDate.of(2024, 1, 17))); // 周三
        assertTrue(tradingTimeChecker.isWeekday(LocalDate.of(2024, 1, 18))); // 周四
        assertTrue(tradingTimeChecker.isWeekday(LocalDate.of(2024, 1, 19))); // 周五
        
        // 周六周日
        assertFalse(tradingTimeChecker.isWeekday(LocalDate.of(2024, 1, 20))); // 周六
        assertFalse(tradingTimeChecker.isWeekday(LocalDate.of(2024, 1, 21))); // 周日
    }
    
    @Test
    void testGetTradingSession() {
        // 盘前
        LocalDateTime beforeOpen = LocalDateTime.of(2024, 1, 15, 8, 0);
        assertEquals("盘前", tradingTimeChecker.getTradingSession(beforeOpen));
        
        // 早盘
        LocalDateTime morning = LocalDateTime.of(2024, 1, 15, 10, 0);
        assertEquals("早盘", tradingTimeChecker.getTradingSession(morning));
        
        // 午休
        LocalDateTime lunch = LocalDateTime.of(2024, 1, 15, 12, 0);
        assertEquals("午休", tradingTimeChecker.getTradingSession(lunch));
        
        // 午盘
        LocalDateTime afternoon = LocalDateTime.of(2024, 1, 15, 14, 0);
        assertEquals("午盘", tradingTimeChecker.getTradingSession(afternoon));
        
        // 收盘
        LocalDateTime afterClose = LocalDateTime.of(2024, 1, 15, 15, 30);
        assertEquals("收盘", tradingTimeChecker.getTradingSession(afterClose));
        
        // 非交易日
        LocalDateTime weekend = LocalDateTime.of(2024, 1, 20, 10, 0);
        assertEquals("非交易日", tradingTimeChecker.getTradingSession(weekend));
    }
}