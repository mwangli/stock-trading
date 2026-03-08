package com.stock.tradingExecutor.execution;

import com.stock.tradingExecutor.domain.vo.AccountStatus;
import com.stock.tradingExecutor.domain.vo.OrderResult;
import com.stock.tradingExecutor.domain.entity.OrderStatus;
import com.stock.tradingExecutor.domain.entity.Position;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 模拟券商适配器
 * 用于测试和模拟交易
 */
@Slf4j
@Component
@org.springframework.context.annotation.Primary
public class MockBrokerAdapter implements BrokerAdapter {

    /**
     * 模拟账户资金
     */
    private BigDecimal mockAvailableCash = new BigDecimal("1000000");

    /**
     * 模拟持仓
     */
    private final Map<String, Position> mockPositions = new ConcurrentHashMap<>();

    /**
     * 模拟订单
     */
    private final Map<String, OrderResult> mockOrders = new ConcurrentHashMap<>();

    /**
     * 模拟价格缓存
     */
    private final Map<String, BigDecimal> mockPrices = new ConcurrentHashMap<>();

    @Override
    public String getName() {
        return "MockBroker";
    }

    @Override
    public AccountStatus getAccountInfo() {
        log.info("[MockBroker] 获取账户资金信息");

        AccountStatus status = new AccountStatus();
        status.setAvailableCash(mockAvailableCash);
        status.setFrozenAmount(BigDecimal.ZERO);

        // 计算持仓市值
        BigDecimal positionValue = mockPositions.values().stream()
                .map(p -> p.getCurrentPrice().multiply(BigDecimal.valueOf(p.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        status.setTotalPosition(positionValue);
        status.setTotalAssets(mockAvailableCash.add(positionValue));
        status.setDailyProfitLossPercent(0.0);
        status.setMonthlyProfitLossPercent(0.0);

        return status;
    }

    @Override
    public BigDecimal getRealtimePrice(String stockCode) {
        log.info("[MockBroker] 获取股票实时价格: {}", stockCode);

        // 模拟价格：如果缓存中有则使用，否则生成随机价格
        return mockPrices.computeIfAbsent(stockCode, code -> {
            Random random = new Random();
            double basePrice = 10 + random.nextDouble() * 90; // 10-100元
            return BigDecimal.valueOf(basePrice).setScale(2, RoundingMode.HALF_UP);
        });
    }

    /**
     * 设置模拟价格
     */
    public void setMockPrice(String stockCode, BigDecimal price) {
        mockPrices.put(stockCode, price);
    }

    @Override
    public OrderResult submitOrder(String direction, String stockCode, BigDecimal price, Integer quantity) {
        log.info("[MockBroker] 提交委托: {} {} {} {}", direction, stockCode, price, quantity);

        String orderId = generateOrderId();

        OrderResult result = OrderResult.builder()
                .success(true)
                .orderId(orderId)
                .stockCode(stockCode)
                .direction(direction)
                .price(price)
                .quantity(quantity)
                .status(OrderStatus.SUBMITTED)
                .message("委托已提交")
                .submitTime(LocalDateTime.now())
                .build();

        mockOrders.put(orderId, result);

        // 模拟成交
        simulateFill(orderId, direction, stockCode, price, quantity);

        return result;
    }

    /**
     * 模拟成交
     */
    private void simulateFill(String orderId, String direction, String stockCode, BigDecimal price, Integer quantity) {
        // 使用线程模拟延迟成交
        new Thread(() -> {
            try {
                Thread.sleep(1000 + new Random().nextInt(2000)); // 1-3秒后成交

                OrderResult order = mockOrders.get(orderId);
                if (order != null && order.getStatus() == OrderStatus.SUBMITTED) {
                    // 更新订单状态
                    OrderResult filledOrder = OrderResult.builder()
                            .success(true)
                            .orderId(orderId)
                            .stockCode(stockCode)
                            .direction(direction)
                            .price(price)
                            .quantity(quantity)
                            .amount(price.multiply(BigDecimal.valueOf(quantity)))
                            .status(OrderStatus.FILLED)
                            .message("已成交")
                            .submitTime(order.getSubmitTime())
                            .fillTime(LocalDateTime.now())
                            .build();

                    mockOrders.put(orderId, filledOrder);

                    // 更新持仓和资金
                    if ("BUY".equals(direction)) {
                        updateBuyPosition(stockCode, price, quantity);
                        mockAvailableCash = mockAvailableCash.subtract(price.multiply(BigDecimal.valueOf(quantity)));
                    } else {
                        updateSellPosition(stockCode, quantity);
                        mockAvailableCash = mockAvailableCash.add(price.multiply(BigDecimal.valueOf(quantity)));
                    }

                    log.info("[MockBroker] 订单成交: {}", orderId);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }).start();
    }

    /**
     * 更新买入持仓
     */
    private void updateBuyPosition(String stockCode, BigDecimal price, Integer quantity) {
        Position position = mockPositions.getOrDefault(stockCode, new Position());
        position.setStockCode(stockCode);
        position.setStockName("模拟股票" + stockCode);

        int oldQuantity = position.getQuantity() != null ? position.getQuantity() : 0;
        BigDecimal oldCost = position.getAvgCost() != null ? position.getAvgCost() : BigDecimal.ZERO;

        // 计算新的平均成本
        BigDecimal totalCost = oldCost.multiply(BigDecimal.valueOf(oldQuantity)).add(price.multiply(BigDecimal.valueOf(quantity)));
        int newQuantity = oldQuantity + quantity;
        BigDecimal newAvgCost = totalCost.divide(BigDecimal.valueOf(newQuantity), 2, RoundingMode.HALF_UP);

        position.setQuantity(newQuantity);
        position.setAvgCost(newAvgCost);
        position.setCurrentPrice(price);

        mockPositions.put(stockCode, position);
    }

    /**
     * 更新卖出持仓
     */
    private void updateSellPosition(String stockCode, Integer quantity) {
        Position position = mockPositions.get(stockCode);
        if (position != null) {
            int newQuantity = position.getQuantity() - quantity;
            if (newQuantity <= 0) {
                mockPositions.remove(stockCode);
            } else {
                position.setQuantity(newQuantity);
            }
        }
    }

    @Override
    public OrderStatus queryOrderStatus(String orderId) {
        log.info("[MockBroker] 查询委托状态: {}", orderId);

        OrderResult order = mockOrders.get(orderId);
        if (order != null) {
            return order.getStatus();
        }
        return OrderStatus.REJECTED;
    }

    @Override
    public Boolean cancelOrder(String orderId) {
        log.info("[MockBroker] 撤销委托: {}", orderId);

        OrderResult order = mockOrders.get(orderId);
        if (order != null && order.getStatus() == OrderStatus.SUBMITTED) {
            OrderResult cancelledOrder = OrderResult.builder()
                    .success(true)
                    .orderId(orderId)
                    .stockCode(order.getStockCode())
                    .direction(order.getDirection())
                    .price(order.getPrice())
                    .quantity(order.getQuantity())
                    .status(OrderStatus.CANCELLED)
                    .message("已撤销")
                    .submitTime(order.getSubmitTime())
                    .fillTime(LocalDateTime.now())
                    .build();

            mockOrders.put(orderId, cancelledOrder);
            return true;
        }
        return false;
    }

    @Override
    public List<OrderResult> getTodayOrders() {
        return new ArrayList<>(mockOrders.values());
    }

    @Override
    public List<Position> getPositions() {
        return new ArrayList<>(mockPositions.values());
    }

    /**
     * 生成订单ID
     */
    private String generateOrderId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase();
    }

    /**
     * 重置模拟数据
     */
    public void reset() {
        mockAvailableCash = new BigDecimal("1000000");
        mockPositions.clear();
        mockOrders.clear();
        mockPrices.clear();
    }
}
