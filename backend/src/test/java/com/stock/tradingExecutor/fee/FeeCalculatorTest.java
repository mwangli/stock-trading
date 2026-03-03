package com.stock.tradingExecutor.fee;

import com.stock.tradingExecutor.config.FeeConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 手续费计算器测试
 */
class FeeCalculatorTest {
    
    private FeeCalculator feeCalculator;
    private FeeConfig feeConfig;
    
    @BeforeEach
    void setUp() {
        feeConfig = new FeeConfig();
        feeConfig.setFeeRate(0.0005);
        feeConfig.setMinFee(5.0);
        
        feeCalculator = new FeeCalculator(feeConfig);
    }
    
    @Test
    void testCalculateFee_NormalAmount() {
        // 正常金额: 10万 * 0.0005 = 50元
        BigDecimal amount = new BigDecimal("100000");
        BigDecimal fee = feeCalculator.calculate(amount);
        
        assertEquals(new BigDecimal("50.00"), fee);
    }
    
    @Test
    void testCalculateFee_SmallAmount() {
        // 小金额: 5000 * 0.0005 = 2.5元，但最低5元
        BigDecimal amount = new BigDecimal("5000");
        BigDecimal fee = feeCalculator.calculate(amount);
        
        assertEquals(new BigDecimal("5.00"), fee);
    }
    
    @Test
    void testCalculateFee_ZeroAmount() {
        // 零金额
        BigDecimal fee = feeCalculator.calculate(BigDecimal.ZERO);
        
        assertEquals(BigDecimal.ZERO, fee);
    }
    
    @Test
    void testCalculateFee_NullAmount() {
        // 空金额
        BigDecimal fee = feeCalculator.calculate(null);
        
        assertEquals(BigDecimal.ZERO, fee);
    }
    
    @Test
    void testCalculateBuyCost() {
        // 买入成本 = 金额 + 手续费
        BigDecimal price = new BigDecimal("10.00");
        int quantity = 1000;
        
        BigDecimal cost = feeCalculator.calculateBuyCost(price, quantity);
        
        // 金额 = 10 * 1000 = 10000
        // 手续费 = 10000 * 0.0005 = 5
        // 总成本 = 10005
        assertEquals(new BigDecimal("10005.00"), cost);
    }
    
    @Test
    void testCalculateSellIncome() {
        // 卖出收入 = 金额 - 手续费
        BigDecimal price = new BigDecimal("10.00");
        int quantity = 1000;
        
        BigDecimal income = feeCalculator.calculateSellIncome(price, quantity);
        
        // 金额 = 10 * 1000 = 10000
        // 手续费 = 10000 * 0.0005 = 5
        // 净收入 = 9995
        assertEquals(new BigDecimal("9995.00"), income);
    }
    
    @Test
    void testCalculateTotalFee() {
        // 双边手续费
        BigDecimal price = new BigDecimal("10.00");
        int quantity = 1000;
        
        BigDecimal totalFee = feeCalculator.calculateTotalFee(price, quantity);
        
        // 单边 = 5，双边 = 10
        assertEquals(new BigDecimal("10.00"), totalFee);
    }
    
    @Test
    void testCalculateBreakEvenPercent() {
        double breakEven = feeCalculator.calculateBreakEvenPercent();
        
        // 双边手续费率 = 0.001 = 0.1%
        assertTrue(breakEven > 0);
        assertTrue(breakEven < 1);
    }
}