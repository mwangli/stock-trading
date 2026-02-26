package com.stock.databus.entity;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

public class StockPriceTest {

    @Test
    public void testStockPriceCreation() {
        System.out.println("测试: 创建StockPrice对象");
        
        StockPrice price = new StockPrice();
        price.setId("test-id");
        price.setCode("600519");
        price.setDate(LocalDate.of(2024, 1, 15));
        price.setPrice1(new BigDecimal("1800.00"));
        price.setPrice2(new BigDecimal("1850.00"));
        price.setPrice3(new BigDecimal("1790.00"));
        price.setPrice4(new BigDecimal("1845.00"));
        price.setTradingVolume(new BigDecimal("1000000"));
        price.setTradingAmount(new BigDecimal("1850000000"));
        price.setAmplitude(new BigDecimal("3.33"));
        price.setIncreaseRate(new BigDecimal("2.50"));
        price.setChangeAmount(new BigDecimal("45.00"));
        price.setExchangeRate(new BigDecimal("0.85"));
        price.setTodayOpenPrice(new BigDecimal("1800.00"));
        price.setYesterdayClosePrice(new BigDecimal("1800.00"));
        price.setCreateTime(LocalDateTime.now());
        
        // 验证字段
        assertEquals("600519", price.getCode());
        assertEquals(LocalDate.of(2024, 1, 15), price.getDate());
        assertEquals(new BigDecimal("1800.00"), price.getPrice1());
        assertEquals(new BigDecimal("1850.00"), price.getPrice2());
        
        System.out.println("StockPrice创建测试通过!");
    }

    @Test
    public void testPriceCalculation() {
        System.out.println("测试: 价格计算");
        
        StockPrice price = new StockPrice();
        price.setPrice1(new BigDecimal("100.00")); // 开盘价
        price.setPrice2(new BigDecimal("110.00")); // 最高价
        price.setPrice3(new BigDecimal("95.00"));  // 最低价
        price.setPrice4(new BigDecimal("105.00")); // 收盘价
        
        // 验证涨跌幅计算
        BigDecimal expectedIncrease = new BigDecimal("5.00");
        // (收盘价 - 开盘价) / 开盘价 * 100
        BigDecimal increaseRate = price.getPrice4()
                .subtract(price.getPrice1())
                .divide(price.getPrice1(), 4, java.math.RoundingMode.HALF_UP)
                .multiply(new BigDecimal("100"));
        
        System.out.println("计算涨跌幅: " + increaseRate + "%");
        assertTrue(increaseRate.compareTo(new BigDecimal("5.00")) == 0);
        
        System.out.println("价格计算测试通过!");
    }

    @Test
    public void testPriceFields() {
        System.out.println("测试: 价格字段验证");
        
        // 验证价格字段名称
        StockPrice price = new StockPrice();
        
        // price1 = 开盘价
        // price2 = 最高价
        // price3 = 最低价  
        // price4 = 收盘价
        
        price.setPrice1(new BigDecimal("10.00"));
        price.setPrice2(new BigDecimal("12.00"));
        price.setPrice3(new BigDecimal("9.00"));
        price.setPrice4(new BigDecimal("11.00"));
        
        assertTrue(price.getPrice2().compareTo(price.getPrice1()) >= 0, "最高价应>=开盘价");
        assertTrue(price.getPrice1().compareTo(price.getPrice3()) >= 0, "开盘价应>=最低价");
        assertTrue(price.getPrice2().compareTo(price.getPrice3()) >= 0, "最高价应>=最低价");
        
        System.out.println("价格字段验证测试通过!");
    }
}
