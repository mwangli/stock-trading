package com.stock.tradingExecutor.broker;

import com.stock.tradingExecutor.entity.OrderResult;
import com.stock.tradingExecutor.enums.OrderStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 模拟券商适配器测试
 */
class MockBrokerAdapterTest {
    
    private MockBrokerAdapter brokerAdapter;
    
    @BeforeEach
    void setUp() {
        brokerAdapter = new MockBrokerAdapter();
    }
    
    @Test
    void testGetName() {
        assertEquals("MockBroker", brokerAdapter.getName());
    }
    
    @Test
    void testGetAccountInfo() {
        var accountInfo = brokerAdapter.getAccountInfo();
        
        assertNotNull(accountInfo);
        assertNotNull(accountInfo.getAvailableCash());
        assertNotNull(accountInfo.getTotalAssets());
        assertTrue(accountInfo.getAvailableCash().compareTo(BigDecimal.ZERO) > 0);
    }
    
    @Test
    void testGetRealtimePrice() {
        BigDecimal price = brokerAdapter.getRealtimePrice("000001");
        
        assertNotNull(price);
        assertTrue(price.compareTo(BigDecimal.ZERO) > 0);
    }
    
    @Test
    void testSetMockPrice() {
        BigDecimal expectedPrice = new BigDecimal("15.50");
        brokerAdapter.setMockPrice("000002", expectedPrice);
        
        BigDecimal actualPrice = brokerAdapter.getRealtimePrice("000002");
        
        assertEquals(expectedPrice, actualPrice);
    }
    
    @Test
    void testSubmitOrder() {
        OrderResult result = brokerAdapter.submitOrder("BUY", "000001", new BigDecimal("10.00"), 100);
        
        assertTrue(result.isSuccess());
        assertNotNull(result.getOrderId());
        assertEquals("000001", result.getStockCode());
        assertEquals("BUY", result.getDirection());
        assertEquals(OrderStatus.SUBMITTED, result.getStatus());
    }
    
    @Test
    void testQueryOrderStatus() throws InterruptedException {
        // 提交订单
        OrderResult order = brokerAdapter.submitOrder("BUY", "000001", new BigDecimal("10.00"), 100);
        
        // 初始状态
        OrderStatus initialStatus = brokerAdapter.queryOrderStatus(order.getOrderId());
        assertTrue(initialStatus == OrderStatus.SUBMITTED || initialStatus == OrderStatus.FILLED);
        
        // 等待成交
        Thread.sleep(3000);
        
        // 最终状态
        OrderStatus finalStatus = brokerAdapter.queryOrderStatus(order.getOrderId());
        assertEquals(OrderStatus.FILLED, finalStatus);
    }
    
    @Test
    void testCancelOrder() {
        // 提交订单
        OrderResult order = brokerAdapter.submitOrder("BUY", "000001", new BigDecimal("10.00"), 100);
        
        // 尝试撤单 (可能已经成交)
        Boolean cancelled = brokerAdapter.cancelOrder(order.getOrderId());
        
        // 验证状态
        OrderStatus status = brokerAdapter.queryOrderStatus(order.getOrderId());
        assertTrue(status == OrderStatus.CANCELLED || status == OrderStatus.FILLED);
    }
    
    @Test
    void testGetTodayOrders() {
        // 提交几个订单
        brokerAdapter.submitOrder("BUY", "000001", new BigDecimal("10.00"), 100);
        brokerAdapter.submitOrder("BUY", "000002", new BigDecimal("20.00"), 100);
        
        var orders = brokerAdapter.getTodayOrders();
        
        assertNotNull(orders);
        assertTrue(orders.size() >= 2);
    }
    
    @Test
    void testGetPositions() throws InterruptedException {
        // 提交买入订单
        brokerAdapter.submitOrder("BUY", "000001", new BigDecimal("10.00"), 100);
        
        // 等待成交
        Thread.sleep(3000);
        
        var positions = brokerAdapter.getPositions();
        
        assertNotNull(positions);
        assertFalse(positions.isEmpty());
    }
    
    @Test
    void testReset() {
        // 提交订单
        brokerAdapter.submitOrder("BUY", "000001", new BigDecimal("10.00"), 100);
        
        // 重置
        brokerAdapter.reset();
        
        // 验证清空
        var orders = brokerAdapter.getTodayOrders();
        var positions = brokerAdapter.getPositions();
        
        assertTrue(orders.isEmpty());
        assertTrue(positions.isEmpty());
    }
}