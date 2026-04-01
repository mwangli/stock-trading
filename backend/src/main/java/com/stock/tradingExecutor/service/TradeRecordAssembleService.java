package com.stock.tradingExecutor.service;

import com.stock.tradingExecutor.domain.entity.HistoryOrder;
import com.stock.tradingExecutor.domain.entity.TradeRecord;
import com.stock.tradingExecutor.persistence.HistoryOrderRepository;
import com.stock.tradingExecutor.persistence.TradeRecordRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 交易记录组装服务
 * 将历史订单数据组装成完整的交易记录
 *
 * @author mwangli
 * @since 2026-04-01
 */
@Slf4j
@Service
public class TradeRecordAssembleService {

    private final HistoryOrderRepository historyOrderRepository;
    private final TradeRecordRepository tradeRecordRepository;

    @Autowired
    public TradeRecordAssembleService(HistoryOrderRepository historyOrderRepository,
                                     TradeRecordRepository tradeRecordRepository) {
        this.historyOrderRepository = historyOrderRepository;
        this.tradeRecordRepository = tradeRecordRepository;
    }

    /**
     * 组装交易记录
     * 对已同步的订单数据进行分析，将同一股票的买入订单与对应的卖出订单进行匹配组合
     */
    @Transactional
    public void assembleTradeRecords() {
        log.info("[交易记录组装] 开始组装交易记录");

        // 获取所有历史订单
        List<HistoryOrder> allOrders = historyOrderRepository.findAll();
        log.info("[交易记录组装] 获取到 {} 条历史订单", allOrders.size());

        // 按股票代码分组
        Map<String, List<HistoryOrder>> ordersByStock = allOrders.stream()
                .collect(Collectors.groupingBy(HistoryOrder::getStockCode));

        int totalTrades = 0;
        int updatedTrades = 0;
        int newTrades = 0;

        for (Map.Entry<String, List<HistoryOrder>> entry : ordersByStock.entrySet()) {
            String stockCode = entry.getKey();
            List<HistoryOrder> orders = entry.getValue();

            log.info("[交易记录组装] 处理股票: {}，共 {} 条订单", stockCode, orders.size());

            // 按时间排序
            orders.sort(Comparator.comparing(HistoryOrder::getOrderSubmitTime));

            // 分离买入和卖出订单
            List<HistoryOrder> buyOrders = orders.stream()
                    .filter(HistoryOrder::isBuy)
                    .collect(Collectors.toList());

            List<HistoryOrder> sellOrders = orders.stream()
                    .filter(HistoryOrder::isSell)
                    .collect(Collectors.toList());

            // 按照先进先出原则匹配买入和卖出订单
            List<TradeRecord> tradeRecords = matchOrders(buyOrders, sellOrders);

            // 处理每个交易记录
            for (TradeRecord tradeRecord : tradeRecords) {
                processTradeRecord(tradeRecord);
                totalTrades++;
                if (tradeRecordRepository.existsByTradeId(tradeRecord.getTradeId())) {
                    updatedTrades++;
                } else {
                    newTrades++;
                }
            }
        }

        log.info("[交易记录组装] 组装完成，共处理 {} 条交易记录，其中新增 {} 条，更新 {} 条",
                totalTrades, newTrades, updatedTrades);
    }

    /**
     * 按照先进先出原则匹配买入和卖出订单
     */
    private List<TradeRecord> matchOrders(List<HistoryOrder> buyOrders, List<HistoryOrder> sellOrders) {
        List<TradeRecord> tradeRecords = new ArrayList<>();

        // 买入订单栈（先进先出）
        Deque<HistoryOrder> buyOrderQueue = new LinkedList<>(buyOrders);
        // 卖出订单栈
        Deque<HistoryOrder> sellOrderQueue = new LinkedList<>(sellOrders);

        while (!buyOrderQueue.isEmpty() && !sellOrderQueue.isEmpty()) {
            HistoryOrder buyOrder = buyOrderQueue.peek();
            HistoryOrder sellOrder = sellOrderQueue.peek();

            // 确保买入时间早于卖出时间
            if (buyOrder.getOrderSubmitTime().isAfter(sellOrder.getOrderSubmitTime())) {
                // 跳过时间异常的卖出订单
                log.warn("[交易记录组装] 卖出订单时间早于买入订单时间，跳过: sellOrderNo={}, buyOrderNo={}",
                        sellOrder.getOrderNo(), buyOrder.getOrderNo());
                sellOrderQueue.poll();
                continue;
            }

            // 计算匹配数量
            int matchQuantity = Math.min(buyOrder.getQuantity(), sellOrder.getQuantity());

            // 创建或更新交易记录
            TradeRecord tradeRecord = createOrUpdateTradeRecord(buyOrder, sellOrder, matchQuantity);
            tradeRecords.add(tradeRecord);

            // 更新订单数量
            buyOrder.setQuantity(buyOrder.getQuantity() - matchQuantity);
            sellOrder.setQuantity(sellOrder.getQuantity() - matchQuantity);

            // 移除已完全匹配的订单
            if (buyOrder.getQuantity() <= 0) {
                buyOrderQueue.poll();
            }
            if (sellOrder.getQuantity() <= 0) {
                sellOrderQueue.poll();
            }
        }

        return tradeRecords;
    }

    /**
     * 创建或更新交易记录
     */
    private TradeRecord createOrUpdateTradeRecord(HistoryOrder buyOrder, HistoryOrder sellOrder, int matchQuantity) {
        // 生成交易ID
        String tradeId = generateTradeId(buyOrder.getOrderNo(), sellOrder.getOrderNo());

        // 检查是否已存在
        Optional<TradeRecord> existingRecord = tradeRecordRepository.findByTradeId(tradeId);

        TradeRecord tradeRecord;
        if (existingRecord.isPresent()) {
            tradeRecord = existingRecord.get();
            // 更新现有记录
            updateTradeRecord(tradeRecord, sellOrder, matchQuantity);
        } else {
            // 创建新记录
            tradeRecord = createNewTradeRecord(buyOrder, sellOrder, matchQuantity);
        }

        return tradeRecord;
    }

    /**
     * 生成交易ID
     */
    private String generateTradeId(String buyOrderNo, String sellOrderNo) {
        // 格式: 买入订单号_卖出订单号
        return buyOrderNo + "_" + sellOrderNo;
    }

    /**
     * 创建新的交易记录
     */
    private TradeRecord createNewTradeRecord(HistoryOrder buyOrder, HistoryOrder sellOrder, int matchQuantity) {
        TradeRecord tradeRecord = new TradeRecord();
        tradeRecord.setTradeId(generateTradeId(buyOrder.getOrderNo(), sellOrder.getOrderNo()));
        tradeRecord.setStockCode(buyOrder.getStockCode());
        tradeRecord.setStockName(buyOrder.getStockName());
        tradeRecord.setBuyOrderNo(buyOrder.getOrderNo());
        tradeRecord.setSellOrderNos(sellOrder.getOrderNo());
        tradeRecord.setTradeDate(buyOrder.getOrderDate());
        tradeRecord.setBuyTime(buyOrder.getOrderSubmitTime());
        tradeRecord.setLastSellTime(sellOrder.getOrderSubmitTime());

        // 计算买入和卖出金额
        BigDecimal buyAmount = buyOrder.getPrice().multiply(BigDecimal.valueOf(matchQuantity));
        BigDecimal sellAmount = sellOrder.getPrice().multiply(BigDecimal.valueOf(matchQuantity));

        tradeRecord.setBuyAmount(buyAmount);
        tradeRecord.setSellAmount(sellAmount);
        tradeRecord.setProfitAmount(sellAmount.subtract(buyAmount));

        // 计算手续费
        calculateFees(tradeRecord, buyAmount, sellAmount);

        // 计算净收益
        tradeRecord.setNetProfitAmount(tradeRecord.getProfitAmount().subtract(tradeRecord.getTotalFee()));

        // 计算收益率
        calculateReturnRates(tradeRecord);

        // 计算持仓时间
        calculateHoldingTime(tradeRecord);

        tradeRecord.setStatus("COMPLETED");
        tradeRecord.setRemark("自动组装交易记录");

        return tradeRecord;
    }

    /**
     * 更新交易记录
     */
    private void updateTradeRecord(TradeRecord tradeRecord, HistoryOrder sellOrder, int matchQuantity) {
        // 更新卖出订单号列表
        String existingSellOrderNos = tradeRecord.getSellOrderNos();
        if (!existingSellOrderNos.contains(sellOrder.getOrderNo())) {
            tradeRecord.setSellOrderNos(existingSellOrderNos + "," + sellOrder.getOrderNo());
        }

        // 更新卖出总金额
        BigDecimal additionalSellAmount = sellOrder.getPrice().multiply(BigDecimal.valueOf(matchQuantity));
        tradeRecord.setSellAmount(tradeRecord.getSellAmount().add(additionalSellAmount));

        // 更新收益金额
        tradeRecord.setProfitAmount(tradeRecord.getSellAmount().subtract(tradeRecord.getBuyAmount()));

        // 重新计算手续费
        calculateFees(tradeRecord, tradeRecord.getBuyAmount(), tradeRecord.getSellAmount());

        // 重新计算净收益
        tradeRecord.setNetProfitAmount(tradeRecord.getProfitAmount().subtract(tradeRecord.getTotalFee()));

        // 重新计算收益率
        calculateReturnRates(tradeRecord);

        // 更新最后卖出时间
        if (sellOrder.getOrderSubmitTime().isAfter(tradeRecord.getLastSellTime())) {
            tradeRecord.setLastSellTime(sellOrder.getOrderSubmitTime());
            // 重新计算持仓时间
            calculateHoldingTime(tradeRecord);
        }

        tradeRecord.setStatus("COMPLETED");
    }

    /**
     * 计算手续费
     */
    private void calculateFees(TradeRecord tradeRecord, BigDecimal buyAmount, BigDecimal sellAmount) {
        // 假设手续费费率
        BigDecimal buyFeeRate = new BigDecimal("0.0003"); // 0.03%
        BigDecimal sellFeeRate = new BigDecimal("0.0003"); // 0.03%
        BigDecimal transferFeeRate = new BigDecimal("0.00002"); // 0.002%

        // 计算买入手续费
        BigDecimal buyFee = buyAmount.multiply(buyFeeRate).setScale(4, RoundingMode.HALF_UP);
        // 最低手续费 5 元
        if (buyFee.compareTo(new BigDecimal("5")) < 0) {
            buyFee = new BigDecimal("5");
        }

        // 计算卖出手续费
        BigDecimal sellFee = sellAmount.multiply(sellFeeRate).setScale(4, RoundingMode.HALF_UP);
        // 最低手续费 5 元
        if (sellFee.compareTo(new BigDecimal("5")) < 0) {
            sellFee = new BigDecimal("5");
        }

        // 计算过户费（仅上海股票）
        BigDecimal transferFee = BigDecimal.ZERO;
        if (tradeRecord.getStockCode().startsWith("6")) {
            transferFee = buyAmount.add(sellAmount).multiply(transferFeeRate).setScale(4, RoundingMode.HALF_UP);
        }

        tradeRecord.setBuyFee(buyFee);
        tradeRecord.setSellFee(sellFee);
        tradeRecord.setOtherFee(transferFee);
        tradeRecord.setTotalFee(buyFee.add(sellFee).add(transferFee));
    }

    /**
     * 计算收益率
     */
    private void calculateReturnRates(TradeRecord tradeRecord) {
        BigDecimal buyAmount = tradeRecord.getBuyAmount();
        if (buyAmount.compareTo(BigDecimal.ZERO) == 0) {
            tradeRecord.setTotalReturnRate(BigDecimal.ZERO);
            tradeRecord.setDailyReturnRate(BigDecimal.ZERO);
            return;
        }

        // 计算总收益率
        BigDecimal totalReturnRate = tradeRecord.getNetProfitAmount()
                .divide(buyAmount, 4, RoundingMode.HALF_UP)
                .multiply(new BigDecimal("100"));
        tradeRecord.setTotalReturnRate(totalReturnRate);

        // 计算日收益率
        if (tradeRecord.getHoldingSeconds() != null && tradeRecord.getHoldingSeconds() > 0) {
            double holdingDays = tradeRecord.getHoldingSeconds() / (24.0 * 3600.0);
            if (holdingDays > 0) {
                BigDecimal dailyReturnRate = totalReturnRate
                        .divide(new BigDecimal(holdingDays), 4, RoundingMode.HALF_UP);
                tradeRecord.setDailyReturnRate(dailyReturnRate);
            } else {
                tradeRecord.setDailyReturnRate(BigDecimal.ZERO);
            }
        } else {
            tradeRecord.setDailyReturnRate(BigDecimal.ZERO);
        }
    }

    /**
     * 计算持仓时间
     */
    private void calculateHoldingTime(TradeRecord tradeRecord) {
        if (tradeRecord.getBuyTime() != null && tradeRecord.getLastSellTime() != null) {
            Duration duration = Duration.between(tradeRecord.getBuyTime(), tradeRecord.getLastSellTime());
            tradeRecord.setHoldingSeconds(duration.getSeconds());
        }
    }

    /**
     * 处理交易记录（保存或更新）
     */
    private void processTradeRecord(TradeRecord tradeRecord) {
        if (tradeRecordRepository.existsByTradeId(tradeRecord.getTradeId())) {
            // 更新现有记录
            tradeRecordRepository.save(tradeRecord);
            log.debug("[交易记录组装] 更新交易记录: tradeId={}", tradeRecord.getTradeId());
        } else {
            // 新增记录
            tradeRecordRepository.save(tradeRecord);
            log.debug("[交易记录组装] 新增交易记录: tradeId={}", tradeRecord.getTradeId());
        }
    }

    /**
     * 在历史订单同步完成后调用
     */
    public void assembleAfterSync() {
        log.info("[交易记录组装] 历史订单同步完成，开始组装交易记录");
        assembleTradeRecords();
    }
}