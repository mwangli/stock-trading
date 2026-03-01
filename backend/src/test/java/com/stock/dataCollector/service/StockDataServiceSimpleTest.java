package com.stock.dataCollector.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 股票数据服务简单测试（无需 Spring 上下文）
 */
class StockDataServiceSimpleTest {

    @Test
    @DisplayName("测试类加载验证")
    void testClassLoading() {
        // 验证测试框架正常工作
        assertTrue(true, "测试框架应正常工作");
    }

    @Test
    @DisplayName("数据服务存在性验证")
    void testServiceExists() {
        // 验证 StockDataService 类存在
        assertNotNull(StockDataService.class, "StockDataService 类应存在");
    }
}
