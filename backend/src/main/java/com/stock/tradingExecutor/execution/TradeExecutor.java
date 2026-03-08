package com.stock.tradingExecutor.execution;

import com.stock.tradingExecutor.broker.BrokerAdapter;
import com.stock.tradingExecutor.config.MonitorConfig;
import com.stock.tradingExecutor.config.PollerConfig;
import com.stock.tradingExecutor.entity.AccountStatus;
import com.stock.tradingExecutor.entity.OrderResult;
import com.stock.tradingExecutor.entity.Position;
import com.stock.tradingExecutor.entity.RiskCheckResult;
import com.stock.tradingExecutor.enums.OrderStatus;
import com.stock.tradingExecutor.fee.FeeCalculator;
import com.stock.tradingExecutor.risk.RiskController;
import com.stock.tradingExecutor.time.TradingTimeChecker;
import com.stock.event.OrderNotificationEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 交易执行器
 * 执行买入/卖出交易，包含完整的价格监控和订单轮询流程
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TradeExecutor {
    
    private final RiskController riskController;
    private final BrokerAdapter brokerAdapter;
    private final PriceMonitor priceMonitor;
    private final OrderPoller orderPoller;
    private final FeeCalculator feeCalculator;
    private final TradingTimeChecker tradingTimeChecker;
    private final MonitorConfig monitorConfig;
    private final PollerConfig pollerConfig;
    
    private final ApplicationEventPublisher eventPublisher;
    /**
     * 并行交易线程池
     */
    private final ExecutorService tradeExecutor = Executors.newCachedThreadPool();
    
    /**
     * 中断标志
     */
    private volatile boolean interrupted = false;
    
    /**
     * 执行买入交易 (带价格监控)
     * 
     * @param stockCode 股票代码
     * @param amount 买入金额
     * @return 订单结果
     */
    public OrderResult executeBuyWithMonitor(String stockCode, BigDecimal amount) {
        log.info("开始执行买入交易 (带价格监控): {} 金额={}", stockCode, amount);
        
        // 1. 风控检查
        RiskCheckResult riskCheck = riskController.checkBeforeBuy(stockCode, amount);
        if (!riskCheck.isPassed()) {
            log.warn("买入风控检查未通过: {}", riskCheck.getViolations());
            return OrderResult.fail("风控检查未通过", riskCheck.getViolations());
        }
        
        // 2. 开始价格监控
        priceMonitor.startMonitor(stockCode, "BUY");
        
        // 3. 价格监控循环
        int maxSamples = pollerConfig.getMaxPollCount();
        int sampleCount = 0;
        
        while (sampleCount < maxSamples && !interrupted) {
            // 采样价格
            priceMonitor.samplePrice(stockCode);
            sampleCount++;
            
            // 检查是否应该执行
            if (priceMonitor.shouldExecuteBuy(stockCode)) {
                log.info("触发买入条件，执行买入: {}", stockCode);
                OrderResult result = doExecuteBuy(stockCode, amount);
                priceMonitor.stopMonitor(stockCode);
                return result;
            }
            
            // 等待下一次采样
            try {
                Thread.sleep(monitorConfig.getSampleIntervalSeconds() * 1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                priceMonitor.stopMonitor(stockCode);
                return OrderResult.fail("买入监控被中断");
            }
        }
        
        priceMonitor.stopMonitor(stockCode);
        
        if (interrupted) {
            return OrderResult.fail("买入任务被中断");
        }
        
        return OrderResult.fail("买入监控超时，未满足触发条件");
    }
    
    /**
     * 执行买入交易 (立即执行，不带价格监控)
     * 
     * @param stockCode 股票代码
     * @param amount 买入金额
     * @return 订单结果
     */
    public OrderResult executeBuy(String stockCode, BigDecimal amount) {
        log.info("执行买入: {} 金额={}", stockCode, amount);
        
        // 1. 风控检查
        RiskCheckResult riskCheck = riskController.checkBeforeBuy(stockCode, amount);
        if (!riskCheck.isPassed()) {
            log.warn("买入风控检查未通过: {}", riskCheck.getViolations());
            return OrderResult.fail("风控检查未通过", riskCheck.getViolations());
        }
        
        return doExecuteBuy(stockCode, amount);
    }
    
    /**
     * 实际执行买入
     */
    private OrderResult doExecuteBuy(String stockCode, BigDecimal amount) {
        try {
            // 获取当前价格
            BigDecimal price = brokerAdapter.getRealtimePrice(stockCode);
            
            // 计算买入数量 (向下取整到100股)
            int quantity = calculateBuyQuantity(amount, price);
            if (quantity < 100) {
                return OrderResult.fail("买入金额不足，无法购买最小单位");
            }
            
            // 计算手续费
            BigDecimal tradeAmount = price.multiply(BigDecimal.valueOf(quantity));
            BigDecimal fee = feeCalculator.calculate(tradeAmount);
            
            // 提交订单
            OrderResult result = brokerAdapter.submitOrder("BUY", stockCode, price, quantity);
            
            if (!result.isSuccess()) {
                return result;
            }
            
            log.info("买入订单提交成功: {} 价格={} 数量={} 手续费={}", stockCode, price, quantity, fee);
            
            // 等待订单完成
            OrderStatus finalStatus = orderPoller.waitForComplete(result.getOrderId());
            
            if (finalStatus == OrderStatus.FILLED) {
                // 更新结果
                result.setStatus(OrderStatus.FILLED);
                result.setAmount(tradeAmount);
                result.setFee(fee);
                result.setFillTime(LocalDateTime.now());
                result.setMessage("买入成交");
                
                log.info("买入成交: {} 价格={} 数量={} 金额={} 手续费={}", 
                        stockCode, price, quantity, tradeAmount, fee);
            } else {
                result.setStatus(finalStatus);
                result.setMessage("买入失败: " + finalStatus.getName());
            }
            
            // 发布通知事件
            eventPublisher.publishEvent(new OrderNotificationEvent(this, result, "BUY"));
            return result;

            
        } catch (Exception e) {
            log.error("买入下单失败: {}", stockCode, e);
            return OrderResult.fail("买入下单失败: " + e.getMessage());
        }
    }
    
    /**
     * 执行卖出交易 (带价格监控)
     * 
     * @param stockCode 股票代码
     * @return 订单结果
     */
    public OrderResult executeSellWithMonitor(String stockCode) {
        log.info("开始执行卖出交易 (带价格监控): {}", stockCode);
        
        // 获取持仓
        Position position = getPosition(stockCode);
        if (position == null) {
            return OrderResult.fail("无该股票持仓");
        }
        
        BigDecimal quantity = BigDecimal.valueOf(position.getQuantity());
        
        // 1. 风控检查
        RiskCheckResult riskCheck = riskController.checkBeforeSell(stockCode, quantity);
        if (!riskCheck.isPassed()) {
            log.warn("卖出风控检查未通过: {}", riskCheck.getViolations());
            return OrderResult.fail("风控检查未通过", riskCheck.getViolations());
        }
        
        // 2. 开始价格监控
        priceMonitor.startMonitor(stockCode, "SELL");
        
        // 3. 价格监控循环
        int maxSamples = pollerConfig.getMaxPollCount();
        int sampleCount = 0;
        
        while (sampleCount < maxSamples && !interrupted) {
            // 采样价格
            priceMonitor.samplePrice(stockCode);
            sampleCount++;
            
            // 检查是否应该执行
            if (priceMonitor.shouldExecuteSell(stockCode)) {
                log.info("触发卖出条件，执行卖出: {}", stockCode);
                OrderResult result = doExecuteSell(stockCode, position);
                priceMonitor.stopMonitor(stockCode);
                return result;
            }
            
            // 等待下一次采样
            try {
                Thread.sleep(monitorConfig.getSampleIntervalSeconds() * 1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                priceMonitor.stopMonitor(stockCode);
                return OrderResult.fail("卖出监控被中断");
            }
        }
        
        priceMonitor.stopMonitor(stockCode);
        
        if (interrupted) {
            return OrderResult.fail("卖出任务被中断");
        }
        
        return OrderResult.fail("卖出监控超时，未满足触发条件");
    }
    
    /**
     * 执行卖出交易 (立即执行，不带价格监控)
     * 
     * @param stockCode 股票代码
     * @param quantity 卖出数量
     * @return 订单结果
     */
    public OrderResult executeSell(String stockCode, BigDecimal quantity) {
        log.info("执行卖出: {} 数量={}", stockCode, quantity);
        
        // 1. 风控检查
        RiskCheckResult riskCheck = riskController.checkBeforeSell(stockCode, quantity);
        if (!riskCheck.isPassed()) {
            log.warn("卖出风控检查未通过: {}", riskCheck.getViolations());
            return OrderResult.fail("风控检查未通过", riskCheck.getViolations());
        }
        
        Position position = getPosition(stockCode);
        if (position == null) {
            return OrderResult.fail("无该股票持仓");
        }
        
        return doExecuteSell(stockCode, position);
    }
    
    /**
     * 实际执行卖出
     */
    private OrderResult doExecuteSell(String stockCode, Position position) {
        try {
            int quantity = position.getQuantity();
            BigDecimal price = brokerAdapter.getRealtimePrice(stockCode);
            
            // 计算手续费
            BigDecimal tradeAmount = price.multiply(BigDecimal.valueOf(quantity));
            BigDecimal fee = feeCalculator.calculate(tradeAmount);
            
            // 提交订单
            OrderResult result = brokerAdapter.submitOrder("SELL", stockCode, price, quantity);
            
            if (!result.isSuccess()) {
                return result;
            }
            
            log.info("卖出订单提交成功: {} 价格={} 数量={} 手续费={}", stockCode, price, quantity, fee);
            
            // 等待订单完成
            OrderStatus finalStatus = orderPoller.waitForComplete(result.getOrderId());
            
            if (finalStatus == OrderStatus.FILLED) {
                // 计算盈亏
                BigDecimal cost = position.getAvgCost().multiply(BigDecimal.valueOf(quantity));
                BigDecimal income = tradeAmount.subtract(fee);
                BigDecimal profit = income.subtract(cost);
                
                // 更新结果
                result.setStatus(OrderStatus.FILLED);
                result.setAmount(tradeAmount);
                result.setFee(fee);
                result.setFillTime(LocalDateTime.now());
                result.setMessage(String.format("卖出成交, 盈亏=%.2f", profit));
                
                log.info("卖出成交: {} 价格={} 数量={} 金额={} 手续费={} 盈亏={}",
                        stockCode, price, quantity, tradeAmount, fee, profit);
            } else {
                result.setStatus(finalStatus);
                result.setMessage("卖出失败: " + finalStatus.getName());
            }
            
            // 发布通知事件
            eventPublisher.publishEvent(new OrderNotificationEvent(this, result, "SELL"));
            return result;

            
        } catch (Exception e) {
            log.error("卖出下单失败: {}", stockCode, e);
            return OrderResult.fail("卖出下单失败: " + e.getMessage());
        }
    }
    
    /**
     * 执行止损卖出
     * 
     * @param stockCode 股票代码
     * @return 订单结果
     */
    public OrderResult executeStopLoss(String stockCode) {
        log.warn("执行止损卖出: {}", stockCode);
        
        Position position = getPosition(stockCode);
        if (position == null) {
            return OrderResult.fail("无该股票持仓");
        }
        
        // 止损卖出不做风控检查，立即执行
        return doExecuteSell(stockCode, position);
    }
    
    /**
     * 执行清仓
     * 
     * @return 清仓结果
     */
    public int executeLiquidation() {
        log.warn("执行清仓操作");
        
        List<Position> positions = brokerAdapter.getPositions();
        int successCount = 0;
        
        for (Position position : positions) {
            try {
                OrderResult result = doExecuteSell(position.getStockCode(), position);
                if (result.isSuccess() && result.getStatus() == OrderStatus.FILLED) {
                    successCount++;
                }
            } catch (Exception e) {
                log.error("清仓失败: {}", position.getStockCode(), e);
            }
        }
        
        log.info("清仓完成: 成功={}/{}", successCount, positions.size());
        return successCount;
    }
    
    /**
     * 并行执行多个买入任务
     * 
     * @param buyTasks 买入任务列表
     * @return 成功数量
     */
    public int executeBuyParallel(List<BuyTask> buyTasks) {
        log.info("并行执行买入任务: 数量={}", buyTasks.size());
        
        CountDownLatch latch = new CountDownLatch(buyTasks.size());
        int[] successCount = {0};
        
        for (BuyTask task : buyTasks) {
            tradeExecutor.submit(() -> {
                try {
                    OrderResult result = executeBuyWithMonitor(task.getStockCode(), task.getAmount());
                    if (result.isSuccess() && result.getStatus() == OrderStatus.FILLED) {
                        synchronized (this) {
                            successCount[0]++;
                        }
                    }
                } catch (Exception e) {
                    log.error("并行买入失败: {}", task.getStockCode(), e);
                } finally {
                    latch.countDown();
                }
            });
            
            // 错开启动时间
            try {
                Thread.sleep(monitorConfig.getSampleIntervalSeconds() * 1000 / 10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        log.info("并行买入完成: 成功={}/{}", successCount[0], buyTasks.size());
        return successCount[0];
    }
    
    /**
     * 中断所有交易任务
     */
    public void interrupt() {
        log.info("中断所有交易任务");
        this.interrupted = true;
    }
    
    /**
     * 重置中断标志
     */
    public void reset() {
        this.interrupted = false;
    }
    
    /**
     * 获取持仓
     */
    private Position getPosition(String stockCode) {
        return brokerAdapter.getPositions().stream()
                .filter(p -> stockCode.equals(p.getStockCode()))
                .findFirst()
                .orElse(null);
    }
    
    /**
     * 计算买入数量 (向下取整到100股)
     */
    private int calculateBuyQuantity(BigDecimal amount, BigDecimal price) {
        if (price == null || price.compareTo(BigDecimal.ZERO) <= 0) {
            return 0;
        }
        
        // 计算可买股数
        int maxQuantity = amount.divide(price, 0, RoundingMode.DOWN).intValue();
        
        // 向下取整到100股
        return (maxQuantity / 100) * 100;
    }
    
    /**
     * 查询订单状态
     */
    public OrderStatus queryOrderStatus(String orderId) {
        return brokerAdapter.queryOrderStatus(orderId);
    }
    
    /**
     * 获取账户状态
     */
    public AccountStatus getAccountStatus() {
        return brokerAdapter.getAccountInfo();
    }
    
    /**
     * 买入任务
     */
    @lombok.Data
    @lombok.AllArgsConstructor
    public static class BuyTask {
        private String stockCode;
        private BigDecimal amount;
    }
}
