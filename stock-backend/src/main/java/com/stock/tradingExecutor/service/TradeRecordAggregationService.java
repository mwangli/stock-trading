// AI_GENERATE_START ---
package com.stock.tradingExecutor.service;

import com.stock.tradingExecutor.domain.entity.BrokerOrderEntity;
import com.stock.tradingExecutor.domain.entity.TradeRecordEntity;
import com.stock.tradingExecutor.execution.FeeCalculator;
import com.stock.tradingExecutor.persistence.BrokerOrderRepository;
import com.stock.tradingExecutor.persistence.TradeRecordRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 将当前账户的已成交历史委托按股票和 FIFO 规则汇总为交易记录。
 * 一笔买入委托形成一个持仓批次，后续卖出可分多次冲减，也可跨多个买入批次分摊。
 *
 * @author mwangli
 * @since 2026-10-11
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TradeRecordAggregationService {

    private static final int MONEY_SCALE = 4;
    private static final int RATE_SCALE = 8;

    private final BrokerOrderRepository brokerOrderRepository;
    private final TradeRecordRepository tradeRecordRepository;
    private final BrokerIdentityService brokerIdentityService;
    private final FeeCalculator feeCalculator;

    /**
     * 使用当前账户全部已成交委托重建交易记录。
     * 派生表先按账户清理后整体重建，保证重复执行结果一致。
     *
     * @return 汇总结果，包含生成记录数和无法匹配的卖出数量
     */
    @Transactional
    public AggregationResult rebuildCurrentAccountTradeRecords() {
        String brokerCode = brokerIdentityService.getBrokerCode();
        String accountId = brokerIdentityService.getAccountId();
        List<BrokerOrderEntity> orders = brokerOrderRepository
                .findByBrokerCodeAndAccountIdAndStatusOrderBySubmittedAtAscIdAsc(
                        brokerCode, accountId, "FILLED");

        tradeRecordRepository.deleteByBrokerCodeAndAccountId(brokerCode, accountId);
        // 先将派生表删除落库，再插入同一买入委托对应的新记录，避免唯一键冲突。
        tradeRecordRepository.flush();

        Map<String, Deque<OpenBuyLot>> openLots = new LinkedHashMap<>();
        List<TradeRecordEntity> records = new ArrayList<>();
        int skippedOrders = 0;
        int unmatchedSellQuantity = 0;

        for (BrokerOrderEntity order : orders) {
            int quantity = resolveQuantity(order);
            BigDecimal price = resolvePrice(order);
            if (!isAggregatable(order, quantity, price)) {
                skippedOrders++;
                continue;
            }

            String positionKey = positionKey(order);
            if ("BUY".equalsIgnoreCase(order.getSide())) {
                OpenBuyLot buyLot = createBuyLot(brokerCode, accountId, order, quantity, price);
                records.add(buyLot.record());
                openLots.computeIfAbsent(positionKey, ignored -> new ArrayDeque<>()).addLast(buyLot);
            } else if ("SELL".equalsIgnoreCase(order.getSide())) {
                unmatchedSellQuantity += matchSellOrder(
                        openLots.computeIfAbsent(positionKey, ignored -> new ArrayDeque<>()),
                        order, quantity, price);
            } else {
                skippedOrders++;
            }
        }

        tradeRecordRepository.saveAll(records);
        log.info("交易记录 FIFO 汇总完成: orders={}, records={}, skippedOrders={}, unmatchedSellQuantity={}",
                orders.size(), records.size(), skippedOrders, unmatchedSellQuantity);
        return new AggregationResult(records.size(), skippedOrders, unmatchedSellQuantity);
    }

    private OpenBuyLot createBuyLot(String brokerCode, String accountId, BrokerOrderEntity order,
                                    int quantity, BigDecimal price) {
        BigDecimal buyAmount = amount(price, quantity);
        TradeRecordEntity record = new TradeRecordEntity()
                .setBrokerCode(brokerCode)
                .setAccountId(accountId)
                .setStockCode(order.getStockCode())
                .setStockName(order.getStockName())
                .setMarket(order.getMarket())
                .setBuyOrderNo(order.getBrokerOrderNo())
                .setStatus("OPEN")
                .setBuyTime(order.getSubmittedAt())
                .setBuyQuantity(quantity)
                .setSoldQuantity(0)
                .setRemainingQuantity(quantity)
                .setBuyPrice(price)
                .setBuyAmount(buyAmount)
                .setSoldCostAmount(BigDecimal.ZERO.setScale(MONEY_SCALE))
                .setSellAmount(BigDecimal.ZERO.setScale(MONEY_SCALE))
                .setBuyFee(feeCalculator.calculate(buyAmount).setScale(MONEY_SCALE))
                .setAllocatedBuyFee(BigDecimal.ZERO.setScale(MONEY_SCALE))
                .setSellFee(BigDecimal.ZERO.setScale(MONEY_SCALE))
                .setGrossProfit(BigDecimal.ZERO.setScale(MONEY_SCALE))
                .setNetProfit(BigDecimal.ZERO.setScale(MONEY_SCALE))
                .setGrossReturnRate(BigDecimal.ZERO.setScale(RATE_SCALE))
                .setReturnRate(BigDecimal.ZERO.setScale(RATE_SCALE))
                .setDailyReturnRate(BigDecimal.ZERO.setScale(RATE_SCALE));
        return new OpenBuyLot(record, quantity, BigDecimal.ZERO, new LinkedHashSet<>());
    }

    private int matchSellOrder(Deque<OpenBuyLot> lots, BrokerOrderEntity sellOrder,
                               int sellQuantity, BigDecimal sellPrice) {
        int remainingSellQuantity = sellQuantity;
        BigDecimal sellOrderAmount = amount(sellPrice, sellQuantity);
        BigDecimal sellOrderFee = feeCalculator.calculate(sellOrderAmount);

        while (remainingSellQuantity > 0 && !lots.isEmpty()) {
            OpenBuyLot lot = lots.peekFirst();
            int matchedQuantity = Math.min(remainingSellQuantity, lot.remainingQuantity());
            BigDecimal allocatedSellFee = sellOrderFee
                    .multiply(BigDecimal.valueOf(matchedQuantity))
                    .divide(BigDecimal.valueOf(sellQuantity), MONEY_SCALE, RoundingMode.HALF_UP);
            applySellMatch(lot, sellOrder, matchedQuantity, sellPrice, allocatedSellFee);
            lot.setRemainingQuantity(lot.remainingQuantity() - matchedQuantity);
            remainingSellQuantity -= matchedQuantity;
            if (lot.remainingQuantity() == 0) {
                lots.removeFirst();
            }
        }

        if (remainingSellQuantity > 0) {
            log.warn("卖出委托缺少可匹配买入批次: stockCode={}, sellOrderNo={}, unmatchedQuantity={}",
                    sellOrder.getStockCode(), sellOrder.getBrokerOrderNo(), remainingSellQuantity);
        }
        return remainingSellQuantity;
    }

    private void applySellMatch(OpenBuyLot lot, BrokerOrderEntity sellOrder, int matchedQuantity,
                                BigDecimal sellPrice, BigDecimal allocatedSellFee) {
        TradeRecordEntity record = lot.record();
        LocalDateTime sellTime = sellOrder.getSubmittedAt();
        int soldQuantity = record.getSoldQuantity() + matchedQuantity;
        int remainingQuantity = record.getBuyQuantity() - soldQuantity;
        long holdingDays = Math.max(0, ChronoUnit.DAYS.between(
                record.getBuyTime().toLocalDate(), sellTime.toLocalDate()));

        lot.sellOrderNos().add(sellOrder.getBrokerOrderNo());
        lot.setWeightedQuantityDays(lot.weightedQuantityDays().add(
                BigDecimal.valueOf(holdingDays).multiply(BigDecimal.valueOf(matchedQuantity))));

        BigDecimal soldCostAmount = record.getSoldCostAmount().add(amount(record.getBuyPrice(), matchedQuantity));
        BigDecimal sellAmount = record.getSellAmount().add(amount(sellPrice, matchedQuantity));
        BigDecimal allocatedBuyFee = record.getBuyFee()
                .multiply(BigDecimal.valueOf(soldQuantity))
                .divide(BigDecimal.valueOf(record.getBuyQuantity()), MONEY_SCALE, RoundingMode.HALF_UP);
        BigDecimal totalSellFee = record.getSellFee().add(allocatedSellFee).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        BigDecimal grossProfit = sellAmount.subtract(soldCostAmount);
        BigDecimal netProfit = grossProfit.subtract(allocatedBuyFee).subtract(totalSellFee);
        BigDecimal grossReturnRate = divide(grossProfit, soldCostAmount, RATE_SCALE);
        BigDecimal netCost = soldCostAmount.add(allocatedBuyFee);
        BigDecimal returnRate = divide(netProfit, netCost, RATE_SCALE);
        BigDecimal weightedHoldingDays = lot.weightedQuantityDays()
                .divide(BigDecimal.valueOf(soldQuantity), 4, RoundingMode.HALF_UP);

        record.setSellOrderNos(String.join(",", lot.sellOrderNos()))
                .setStatus(remainingQuantity == 0 ? "CLOSED" : "PARTIAL")
                .setFirstSellTime(record.getFirstSellTime() != null ? record.getFirstSellTime() : sellTime)
                .setLastSellTime(sellTime)
                .setSoldQuantity(soldQuantity)
                .setRemainingQuantity(remainingQuantity)
                .setAverageSellPrice(divide(sellAmount, BigDecimal.valueOf(soldQuantity), MONEY_SCALE))
                .setSoldCostAmount(soldCostAmount.setScale(MONEY_SCALE, RoundingMode.HALF_UP))
                .setSellAmount(sellAmount.setScale(MONEY_SCALE, RoundingMode.HALF_UP))
                .setAllocatedBuyFee(allocatedBuyFee)
                .setSellFee(totalSellFee)
                .setGrossProfit(grossProfit.setScale(MONEY_SCALE, RoundingMode.HALF_UP))
                .setNetProfit(netProfit.setScale(MONEY_SCALE, RoundingMode.HALF_UP))
                .setGrossReturnRate(grossReturnRate)
                .setReturnRate(returnRate)
                .setHoldingDays(weightedHoldingDays)
                .setDailyReturnRate(calculateDailyReturnRate(returnRate, weightedHoldingDays));
    }

    private BigDecimal calculateDailyReturnRate(BigDecimal returnRate, BigDecimal holdingDays) {
        double effectiveDays = Math.max(1.0D, holdingDays.doubleValue());
        double compoundBase = 1.0D + returnRate.doubleValue();
        if (compoundBase <= 0.0D) {
            return BigDecimal.valueOf(-1).setScale(RATE_SCALE);
        }
        double dailyRate = Math.pow(compoundBase, 1.0D / effectiveDays) - 1.0D;
        return BigDecimal.valueOf(dailyRate).setScale(RATE_SCALE, RoundingMode.HALF_UP);
    }

    private boolean isAggregatable(BrokerOrderEntity order, int quantity, BigDecimal price) {
        return order.getSubmittedAt() != null
                && order.getStockCode() != null && !order.getStockCode().isBlank()
                && order.getBrokerOrderNo() != null && !order.getBrokerOrderNo().isBlank()
                && quantity > 0
                && price != null && price.compareTo(BigDecimal.ZERO) > 0;
    }

    private int resolveQuantity(BrokerOrderEntity order) {
        return order.getFilledQuantity() != null ? order.getFilledQuantity() : 0;
    }

    private BigDecimal resolvePrice(BrokerOrderEntity order) {
        return order.getAverageFillPrice();
    }

    private String positionKey(BrokerOrderEntity order) {
        return order.getStockCode().trim();
    }

    private BigDecimal amount(BigDecimal price, int quantity) {
        return price.multiply(BigDecimal.valueOf(quantity)).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    private BigDecimal divide(BigDecimal numerator, BigDecimal denominator, int scale) {
        if (denominator == null || denominator.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO.setScale(scale);
        }
        return numerator.divide(denominator, scale, RoundingMode.HALF_UP);
    }

    /**
     * 交易记录重建结果。
     *
     * @param recordCount 生成的买入批次记录数
     * @param skippedOrderCount 因关键字段缺失跳过的委托数
     * @param unmatchedSellQuantity 缺少历史买入批次的卖出数量
     */
    public record AggregationResult(int recordCount, int skippedOrderCount, int unmatchedSellQuantity) {
    }

    private static final class OpenBuyLot {
        private final TradeRecordEntity record;
        private int remainingQuantity;
        private BigDecimal weightedQuantityDays;
        private final Set<String> sellOrderNos;

        private OpenBuyLot(TradeRecordEntity record, int remainingQuantity,
                           BigDecimal weightedQuantityDays, Set<String> sellOrderNos) {
            this.record = record;
            this.remainingQuantity = remainingQuantity;
            this.weightedQuantityDays = weightedQuantityDays;
            this.sellOrderNos = sellOrderNos;
        }

        private TradeRecordEntity record() {
            return record;
        }

        private int remainingQuantity() {
            return remainingQuantity;
        }

        private void setRemainingQuantity(int remainingQuantity) {
            this.remainingQuantity = remainingQuantity;
        }

        private BigDecimal weightedQuantityDays() {
            return weightedQuantityDays;
        }

        private void setWeightedQuantityDays(BigDecimal weightedQuantityDays) {
            this.weightedQuantityDays = weightedQuantityDays;
        }

        private Set<String> sellOrderNos() {
            return sellOrderNos;
        }
    }
}
// AI_GENERATE_END ---
