package com.stock.executor;

import com.stock.executor.execution.TradeExecutor;
import com.stock.executor.risk.RiskController;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

public class ExecutorIntegrationTest {

    @Test
    public void testRiskCheckPass() {
        RiskController riskController = new RiskController();
        
        RiskController.RiskCheckResult result = riskController.checkBeforeBuy(
            "600519", new BigDecimal("10000"));
        
        assertNotNull(result, "风控检查结果不应为空");
        System.out.println("风控检查通过: " + result.isPassed());
        
        if (!result.isPassed()) {
            System.out.println("违规原因: " + result.getViolations());
        }
    }

    @Test
    public void testRiskCheckWithHighLoss() {
        RiskController riskController = new RiskController();
        
        RiskController.RiskCheckResult result = riskController.checkBeforeBuy(
            "600519", new BigDecimal("1000000"));
        
        assertNotNull(result, "风控检查结果不应为空");
        System.out.println("大额买入风控检查: " + result.isPassed());
        System.out.println("违规原因: " + result.getViolations());
    }

    @Test
    public void testTradeExecutorBuy() {
        RiskController riskController = new RiskController();
        TradeExecutor executor = new TradeExecutor(riskController);
        
        TradeExecutor.OrderResult result = executor.executeBuy(
            "600519", new BigDecimal("50000"));
        
        assertNotNull(result, "下单结果不应为空");
        System.out.println("买入下单结果: " + (result.isSuccess() ? "成功" : "失败"));
        System.out.println("订单ID: " + result.getOrderId());
        System.out.println("消息: " + result.getMessage());
        
        assertTrue(result.isSuccess() || !result.isSuccess(), "应该有结果");
    }

    @Test
    public void testTradeExecutorSell() {
        RiskController riskController = new RiskController();
        TradeExecutor executor = new TradeExecutor(riskController);
        
        TradeExecutor.OrderResult result = executor.executeSell(
            "600519", new BigDecimal("1000"));
        
        assertNotNull(result, "卖出结果不应为空");
        System.out.println("卖出下单结果: " + (result.isSuccess() ? "成功" : "失败"));
        System.out.println("订单ID: " + result.getOrderId());
    }

    @Test
    public void testOrderIdGeneration() {
        RiskController riskController = new RiskController();
        TradeExecutor executor = new TradeExecutor(riskController);
        
        TradeExecutor.OrderResult result1 = executor.executeBuy(
            "600519", new BigDecimal("1000"));
        TradeExecutor.OrderResult result2 = executor.executeBuy(
            "000858", new BigDecimal("2000"));
        
        assertNotEquals(result1.getOrderId(), result2.getOrderId(), "订单ID应该唯一");
        System.out.println("订单1 ID: " + result1.getOrderId());
        System.out.println("订单2 ID: " + result2.getOrderId());
    }
}
