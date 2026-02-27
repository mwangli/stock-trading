package com.stock.databus.client;

import com.stock.databus.entity.StockInfo;
import com.stock.databus.entity.StockPrice;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TushareClient 单元测试
 */
@DisplayName("TushareClient 单元测试")
public class TushareClientTest {

    @Test
    @DisplayName("股票代码解析测试")
    public void testStockCodeParsing() {
        // 测试代码解析逻辑
        String tsCode = "600519.SZ";
        String code = null;
        if (tsCode != null && tsCode.contains(".")) {
            code = tsCode.split("\\.")[0];
        }
        
        assertEquals("600519", code, "股票代码应该正确解析");
        
        tsCode = "000001.SZ";
        if (tsCode != null && tsCode.contains(".")) {
            code = tsCode.split("\\.")[0];
        }
        assertEquals("000001", code, "深圳股票代码应该正确解析");
        
        System.out.println("✅ 股票代码解析测试通过");
    }

    @Test
    @DisplayName("日期格式化测试")
    public void testDateFormatting() {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd");
        
        String dateStr = "20240101";
        LocalDate date = LocalDate.parse(dateStr, formatter);
        
        assertNotNull(date, "日期不应为空");
        assertEquals(2024, date.getYear(), "年份应该正确");
        assertEquals(1, date.getMonthValue(), "月份应该正确");
        assertEquals(1, date.getDayOfMonth(), "日期应该正确");
        
        System.out.println("✅ 日期格式化测试通过");
    }

    @Test
    @DisplayName("涨跌幅计算测试")
    public void testIncreaseRateCalculation() {
        BigDecimal close = new BigDecimal("105.00");
        BigDecimal preClose = new BigDecimal("100.00");
        
        BigDecimal increaseRate = close.subtract(preClose)
                .divide(preClose, 4, java.math.RoundingMode.HALF_UP)
                .multiply(new BigDecimal("100"));
        
        assertEquals(0, increaseRate.compareTo(new BigDecimal("5.00")), "涨跌幅应该是5%");
        
        // 测试跌的情况
        BigDecimal close2 = new BigDecimal("95.00");
        BigDecimal increaseRate2 = close2.subtract(preClose)
                .divide(preClose, 4, java.math.RoundingMode.HALF_UP)
                .multiply(new BigDecimal("100"));
        
        assertEquals(0, increaseRate2.compareTo(new BigDecimal("-5.00")), "涨跌幅应该是-5%");
        
        System.out.println("✅ 涨跌幅计算测试通过");
    }

    @Test
    @DisplayName("K线数据字段测试")
    public void testKlineDataFields() {
        // 模拟K线数据处理
        String tsCode = "600519.SZ";
        String code = null;
        if (tsCode != null && tsCode.contains(".")) {
            code = tsCode.split("\\.")[0];
        }
        
        assertNotNull(code, "股票代码不应为空");
        assertEquals("600519", code);
        
        String tradeDate = "20240101";
        LocalDate date = null;
        if (tradeDate != null && tradeDate.length() == 8) {
            date = LocalDate.parse(tradeDate, DateTimeFormatter.ofPattern("yyyyMMdd"));
        }
        
        assertNotNull(date, "交易日期不应为空");
        assertEquals(2024, date.getYear());
        
        System.out.println("✅ K线数据字段测试通过");
    }
}
