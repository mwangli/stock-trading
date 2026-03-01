package com.stock.tradingExecutor.execution;

import com.stock.tradingExecutor.enums.OrderStatus;
import com.stock.tradingExecutor.risk.RiskController;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class TradeExecutor {

    private final RiskController riskController;

    public OrderResult executeBuy(String stockCode, BigDecimal amount) {
        log.info("执行买入: {}, 金额: {}", stockCode, amount);
        
        // 1. 风控检查
        RiskController.RiskCheckResult riskCheck = riskController.checkBeforeBuy(stockCode, amount);
        if (!riskCheck.isPassed()) {
            log.warn("风控检查未通过: {}", riskCheck.getViolations());
            return OrderResult.fail("风控检查未通过", riskCheck.getViolations());
        }
        
        try {
            // 2. 调用券商API下单
            String orderId = submitOrder(stockCode, "BUY", amount);
            
            // 3. 返回结果
            return OrderResult.builder()
                    .success(true)
                    .orderId(orderId)
                    .stockCode(stockCode)
                    .direction("BUY")
                    .amount(amount)
                    .status(OrderStatus.PENDING)
                    .message("委托已提交")
                    .submitTime(LocalDateTime.now())
                    .build();
                    
        } catch (Exception e) {
            log.error("买入下单失败", e);
            return OrderResult.fail("下单失败: " + e.getMessage());
        }
    }

    public OrderResult executeSell(String stockCode, BigDecimal quantity) {
        log.info("执行卖出: {}, 数量: {}", stockCode, quantity);
        
        // 1. 风控检查
        RiskController.RiskCheckResult riskCheck = riskController.checkBeforeSell(stockCode, quantity);
        if (!riskCheck.isPassed()) {
            log.warn("风控检查未通过: {}", riskCheck.getViolations());
            return OrderResult.fail("风控检查未通过", riskCheck.getViolations());
        }
        
        try {
            // 2. 调用券商API下单
            String orderId = submitOrder(stockCode, "SELL", quantity);
            
            // 3. 返回结果
            return OrderResult.builder()
                    .success(true)
                    .orderId(orderId)
                    .stockCode(stockCode)
                    .direction("SELL")
                    .quantity(quantity)
                    .status(OrderStatus.PENDING)
                    .message("委托已提交")
                    .submitTime(LocalDateTime.now())
                    .build();
                    
        } catch (Exception e) {
            log.error("卖出下单失败", e);
            return OrderResult.fail("下单失败: " + e.getMessage());
        }
    }

    private String submitOrder(String stockCode, String direction, Object amountOrQuantity) {
        // TODO: 实现实际的券商API调用
        // 这里模拟生成订单ID
        String orderId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        log.info("委托提交成功: {} {} {} {}", orderId, stockCode, direction, amountOrQuantity);
        return orderId;
    }

    public OrderStatus queryOrderStatus(String orderId) {
        // TODO: 查询订单状态
        return OrderStatus.PENDING;
    }

    @Data
    @lombok.Builder
    public static class OrderResult {
        private boolean success;
        private String orderId;
        private String stockCode;
        private String direction;
        private BigDecimal amount;
        private BigDecimal quantity;
        private BigDecimal price;
        private OrderStatus status;
        private String message;
        private LocalDateTime submitTime;
        private LocalDateTime fillTime;

        public static OrderResult fail(String message) {
            return OrderResult.builder()
                    .success(false)
                    .message(message)
                    .build();
        }

        public static OrderResult fail(String message, java.util.List<String> violations) {
            return OrderResult.builder()
                    .success(false)
                    .message(message + ": " + String.join(", ", violations))
                    .build();
        }
    }
}
