package com.stock.tradingExecutor.enums;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 订单状态枚举测试
 */
class OrderStatusTest {
    
    @Test
    void testFromCode() {
        assertEquals(OrderStatus.PENDING, OrderStatus.fromCode("PENDING"));
        assertEquals(OrderStatus.SUBMITTED, OrderStatus.fromCode("SUBMITTED"));
        assertEquals(OrderStatus.FILLED, OrderStatus.fromCode("FILLED"));
        assertEquals(OrderStatus.CANCELLED, OrderStatus.fromCode("CANCELLED"));
        assertEquals(OrderStatus.REJECTED, OrderStatus.fromCode("REJECTED"));
        assertEquals(OrderStatus.TIMEOUT, OrderStatus.fromCode("TIMEOUT"));
        
        // 大小写不敏感
        assertEquals(OrderStatus.FILLED, OrderStatus.fromCode("filled"));
        assertEquals(OrderStatus.FILLED, OrderStatus.fromCode("Filled"));
        
        // 未知code返回PENDING
        assertEquals(OrderStatus.PENDING, OrderStatus.fromCode("UNKNOWN"));
    }
    
    @Test
    void testIsFinal() {
        // 终态
        assertTrue(OrderStatus.FILLED.isFinal());
        assertTrue(OrderStatus.CANCELLED.isFinal());
        assertTrue(OrderStatus.REJECTED.isFinal());
        assertTrue(OrderStatus.TIMEOUT.isFinal());
        
        // 非终态
        assertFalse(OrderStatus.PENDING.isFinal());
        assertFalse(OrderStatus.SUBMITTED.isFinal());
        assertFalse(OrderStatus.PARTIAL.isFinal());
    }
    
    @Test
    void testIsSuccess() {
        // 成功状态
        assertTrue(OrderStatus.FILLED.isSuccess());
        
        // 非成功状态
        assertFalse(OrderStatus.PENDING.isSuccess());
        assertFalse(OrderStatus.SUBMITTED.isSuccess());
        assertFalse(OrderStatus.PARTIAL.isSuccess());
        assertFalse(OrderStatus.CANCELLED.isSuccess());
        assertFalse(OrderStatus.REJECTED.isSuccess());
        assertFalse(OrderStatus.TIMEOUT.isSuccess());
    }
    
    @Test
    void testGetNameAndGetCode() {
        assertEquals("待执行", OrderStatus.PENDING.getName());
        assertEquals("PENDING", OrderStatus.PENDING.getCode());
        
        assertEquals("已报", OrderStatus.SUBMITTED.getName());
        assertEquals("SUBMITTED", OrderStatus.SUBMITTED.getCode());
        
        assertEquals("已成交", OrderStatus.FILLED.getName());
        assertEquals("FILLED", OrderStatus.FILLED.getCode());
    }
}