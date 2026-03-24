package com.stock.tradingExecutor.execution;

import com.stock.tradingExecutor.config.MonitorConfig;
import com.stock.tradingExecutor.config.PollerConfig;
import com.stock.tradingExecutor.domain.vo.AccountStatus;
import com.stock.tradingExecutor.domain.vo.OrderResult;
import com.stock.tradingExecutor.domain.entity.OrderStatus;
import com.stock.tradingExecutor.domain.entity.Position;
import com.stock.tradingExecutor.domain.vo.RiskCheckResult;
import com.stock.tradingExecutor.event.OrderNotificationEvent;
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
     */
    public OrderResult executeBuyWithMonitor(String stockCode, BigDecimal amount) {
        log.info("开始执行买入交易 (带价格监控): {} 金额={}", stockCode, amount);

        RiskCheckResult riskCheck = riskController.checkBeforeBuy(stockCode, amount);
        if (!riskCheck.isPassed()) {
            log.warn("买入风控检查未通过: {}", riskCheck.getViolations());
            return OrderResult.fail("风控检查未通过", riskCheck.getViolations());
        }

        priceMonitor.startMonitor(stockCode, "BUY");

        int maxSamples = pollerConfig.getMaxPollCount();
        int sampleCount = 0;

        while (sampleCount < maxSamples && !interrupted) {
            priceMonitor.samplePrice(stockCode);
            sampleCount++;

            if (priceMonitor.shouldExecuteBuy(stockCode)) {
                log.info("触发买入条件，执行买入: {}", stockCode);
                OrderResult result = doExecuteBuy(stockCode, amount);
                priceMonitor.stopMonitor(stockCode);
                return result;
            }

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
     */
    public OrderResult executeBuy(String stockCode, BigDecimal amount) {
        log.info("执行买入: {} 金额={}", stockCode, amount);

        RiskCheckResult riskCheck = riskController.checkBeforeBuy(stockCode, amount);
        if (!riskCheck.isPassed()) {
            log.warn("买入风控检查未通过: {}", riskCheck.getViolations());
            return OrderResult.fail("风控检查未通过", riskCheck.getViolations());
        }

        return doExecuteBuy(stockCode, amount);
    }

    private OrderResult doExecuteBuy(String stockCode, BigDecimal amount) {
        try {
            BigDecimal price = brokerAdapter.getRealtimePrice(stockCode);

            int quantity = calculateBuyQuantity(amount, price);
            if (quantity < 100) {
                return OrderResult.fail("买入金额不足，无法购买最小单位");
            }

            BigDecimal tradeAmount = price.multiply(BigDecimal.valueOf(quantity));
            BigDecimal fee = feeCalculator.calculate(tradeAmount);

            OrderResult result = brokerAdapter.submitOrder("BUY", stockCode, price, quantity);

            if (!result.isSuccess()) {
                return result;
            }

            log.info("买入订单提交成功: {} 价格={} 数量={} 手续费={}", stockCode, price, quantity, fee);

            OrderStatus finalStatus = orderPoller.waitForComplete(result.getOrderId());

            if (finalStatus == OrderStatus.FILLED) {
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

            eventPublisher.publishEvent(new OrderNotificationEvent(this, result, "BUY"));
            return result;

        } catch (Exception e) {
            log.error("买入下单失败: {}", stockCode, e);
            return OrderResult.fail("买入下单失败: " + e.getMessage());
        }
    }

    /**
     * 执行卖出交易 (带价格监控)
     */
    public OrderResult executeSellWithMonitor(String stockCode) {
        log.info("开始执行卖出交易 (带价格监控): {}", stockCode);

        Position position = getPosition(stockCode);
        if (position == null) {
            return OrderResult.fail("无该股票持仓");
        }

        BigDecimal quantity = BigDecimal.valueOf(position.getQuantity());

        RiskCheckResult riskCheck = riskController.checkBeforeSell(stockCode, quantity);
        if (!riskCheck.isPassed()) {
            log.warn("卖出风控检查未通过: {}", riskCheck.getViolations());
            return OrderResult.fail("风控检查未通过", riskCheck.getViolations());
        }

        priceMonitor.startMonitor(stockCode, "SELL");

        int maxSamples = pollerConfig.getMaxPollCount();
        int sampleCount = 0;

        while (sampleCount < maxSamples && !interrupted) {
            priceMonitor.samplePrice(stockCode);
            sampleCount++;

            if (priceMonitor.shouldExecuteSell(stockCode)) {
                log.info("触发卖出条件，执行卖出: {}", stockCode);
                OrderResult result = doExecuteSell(stockCode, position);
                priceMonitor.stopMonitor(stockCode);
                return result;
            }

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
     * 执行卖出交易 (立即执行)
     */
    public OrderResult executeSell(String stockCode, BigDecimal quantity) {
        log.info("执行卖出: {} 数量={}", stockCode, quantity);

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

    private OrderResult doExecuteSell(String stockCode, Position position) {
        try {
            int quantity = position.getQuantity();
            BigDecimal price = brokerAdapter.getRealtimePrice(stockCode);

            BigDecimal tradeAmount = price.multiply(BigDecimal.valueOf(quantity));
            BigDecimal fee = feeCalculator.calculate(tradeAmount);

            OrderResult result = brokerAdapter.submitOrder("SELL", stockCode, price, quantity);

            if (!result.isSuccess()) {
                return result;
            }

            log.info("卖出订单提交成功: {} 价格={} 数量={} 手续费={}", stockCode, price, quantity, fee);

            OrderStatus finalStatus = orderPoller.waitForComplete(result.getOrderId());

            if (finalStatus == OrderStatus.FILLED) {
                BigDecimal cost = position.getAvgCost().multiply(BigDecimal.valueOf(quantity));
                BigDecimal income = tradeAmount.subtract(fee);
                BigDecimal profit = income.subtract(cost);

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

            eventPublisher.publishEvent(new OrderNotificationEvent(this, result, "SELL"));
            return result;

        } catch (Exception e) {
            log.error("卖出下单失败: {}", stockCode, e);
            return OrderResult.fail("卖出下单失败: " + e.getMessage());
        }
    }

    /**
     * 执行止损卖出
     */
    public OrderResult executeStopLoss(String stockCode) {
        log.warn("执行止损卖出: {}", stockCode);

        Position position = getPosition(stockCode);
        if (position == null) {
            return OrderResult.fail("无该股票持仓");
        }

        return doExecuteSell(stockCode, position);
    }

    /**
     * 执行清仓
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

    public void interrupt() {
        log.info("中断所有交易任务");
        this.interrupted = true;
    }

    public void reset() {
        this.interrupted = false;
    }

    private Position getPosition(String stockCode) {
        return brokerAdapter.getPositions().stream()
                .filter(p -> stockCode.equals(p.getStockCode()))
                .findFirst()
                .orElse(null);
    }

    private int calculateBuyQuantity(BigDecimal amount, BigDecimal price) {
        if (price == null || price.compareTo(BigDecimal.ZERO) <= 0) {
            return 0;
        }
        int maxQuantity = amount.divide(price, 0, RoundingMode.DOWN).intValue();
        return (maxQuantity / 100) * 100;
    }

    public OrderStatus queryOrderStatus(String orderId) {
        return brokerAdapter.queryOrderStatus(orderId);
    }

    public AccountStatus getAccountStatus() {
        return brokerAdapter.getAccountInfo();
    }

    @lombok.Data
    @lombok.AllArgsConstructor
    public static class BuyTask {
        private String stockCode;
        private BigDecimal amount;
    }
}
