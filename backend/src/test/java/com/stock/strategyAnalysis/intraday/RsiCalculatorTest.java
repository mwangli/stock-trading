package com.stock.strategyAnalysis.intraday;

import com.stock.strategyAnalysis.entity.MinuteBar;
import lombok.extern.slf4j.Slf4j;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * RsiCalculator 测试类
 */
@Slf4j
class RsiCalculatorTest {

    private RsiCalculator rsiCalculator;

    @BeforeEach
    void setUp() {
        rsiCalculator = new RsiCalculator();
    }

    @Test
    void testCalculateRsi() {
        // 创建测试数据
        List<MinuteBar> bars = createTestBars(20);
        
        // 计算 RSI
        double rsi = rsiCalculator.calculate(bars, 14);
        
        // RSI 应该在 0-100 之间
        assertTrue(rsi >= 0 && rsi <= 100);
    }

    @Test
    void testIsOverbought() {
        // RSI > 75 视为超买
        assertTrue(rsiCalculator.isOverbought(80, 75));
        assertTrue(rsiCalculator.isOverbought(76, 75));
        assertFalse(rsiCalculator.isOverbought(70, 75));
    }

    private List<MinuteBar> createTestBars(int count) {
        List<MinuteBar> bars = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();
        
        for (int i = 0; i < count; i++) {
            bars.add(MinuteBar.builder()
                    .stockCode("600519")
                    .time(now.minusMinutes(count - i))
                    .openPrice(10.0 + i * 0.1)
                    .highPrice(10.1 + i * 0.1)
                    .lowPrice(9.9 + i * 0.1)
                    .closePrice(10.0 + i * 0.1)
                    .volume(1000000)
                    .build());
        }
        
        return bars;
    }
}
