package com.stock.databus.entity;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

public class StockInfoTest {

    @Test
    public void testStockInfoCreation() {
        System.out.println("测试: 创建StockInfo对象");
        
        StockInfo stock = new StockInfo();
        stock.setId(1L);
        stock.setCode("600519");
        stock.setName("贵州茅台");
        stock.setMarket("sh");
        stock.setIsSt(0);
        stock.setIsTradable(1);
        stock.setPrice(new BigDecimal("1850.00"));
        stock.setIncrease(new BigDecimal("2.50"));
        stock.setDeleted("0");
        stock.setCreateTime(LocalDateTime.now());
        stock.setUpdateTime(LocalDateTime.now());
        
        // 验证字段
        assertEquals(1L, stock.getId());
        assertEquals("600519", stock.getCode());
        assertEquals("贵州茅台", stock.getName());
        assertEquals("sh", stock.getMarket());
        assertEquals(0, stock.getIsSt());
        assertEquals(1, stock.getIsTradable());
        assertEquals("0", stock.getDeleted());
        
        System.out.println("StockInfo创建测试通过!");
    }

    @Test
    public void testStockInfoToString() {
        System.out.println("测试: StockInfo toString");
        
        StockInfo stock = new StockInfo();
        stock.setCode("600519");
        stock.setName("贵州茅台");
        stock.setMarket("sh");
        
        String str = stock.toString();
        
        assertTrue(str.contains("600519"));
        assertTrue(str.contains("贵州茅台"));
        
        System.out.println("toString: " + str);
        System.out.println("ToString测试通过!");
    }
}
