// AI_GENERATE_START-----------------------------
package com.stock.trading.service;

import com.stock.data.service.TradingCalendarService;
import com.stock.model.dto.PredictionCandidateBatchDto;
import com.stock.model.dto.PredictionCandidateItemDto;
import com.stock.model.service.PredictionCandidateQueryService;
import com.stock.trading.adapter.ZXBrokerAdapter;
import com.stock.trading.dto.FiveLevelQuoteDto;
import com.stock.trading.dto.RealTradingStatusDto;
import com.stock.trading.dto.TradeExecutionBatchResponseDto;
import com.stock.trading.dto.TradeExecutionResponseDto;
import com.stock.trading.dto.TradingCandidateDto;
import com.stock.trading.dto.TradingCandidateListResponseDto;
import com.stock.trading.entity.OrderStatus;
import com.stock.trading.entity.Position;
import com.stock.trading.enums.BrokerOrderType;
import com.stock.trading.enums.TradingPriceLevel;
import com.stock.trading.vo.AccountStatus;
import com.stock.trading.vo.BrokerOrderSnapshot;
import com.stock.trading.vo.OrderResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BooleanSupplier;
import java.util.stream.Collectors;

/**
 * 模型驱动的真实交易服务。
 * 上午分阶段卖出全部可卖持仓，下午由模型前三名竞争唯一买入执行权。
 *
 * @author mwangli
 * @since 2026-10-27
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RealTradingService {

    private static final LocalTime MORNING_START = LocalTime.of(9, 35);
    private static final LocalTime MORNING_END = LocalTime.of(11, 25);
    private static final LocalTime AFTERNOON_START = LocalTime.of(13, 5);
    private static final LocalTime AFTERNOON_END = LocalTime.of(14, 54);
    private static final int TOP_CANDIDATE_LIMIT = 3;
    private static final int LOT_SIZE = 100;

    private final PredictionCandidateQueryService predictionCandidateQueryService;
    private final ZXBrokerAdapter brokerAdapter;
    private final OrderPoller orderPoller;
    private final TradingCalendarService tradingCalendarService;
    private final AutomaticTradeExecutionBatchService executionBatchService;

    /**
     * 查询真实交易基础状态。
     *
     * @return 券商会话状态
     */
    public RealTradingStatusDto getStatus() {
        return RealTradingStatusDto.builder()
                .brokerAuthenticated(brokerAdapter.isAuthenticated())
                .build();
    }

    /**
     * 查询当日模型排名前三及持仓标记。
     *
     * @return 当日模型排名和持仓摘要
     */
    public TradingCandidateListResponseDto getCandidates() {
        LocalDate today = LocalDate.now();
        PredictionCandidateBatchDto batch = predictionCandidateQueryService
                .findLatestUsableBatch(today)
                .orElse(null);
        if (batch == null) {
            return TradingCandidateListResponseDto.builder()
                    .targetTradeDate(today)
                    .positionSnapshotAvailable(false)
                    .items(List.of())
                    .build();
        }
        boolean hasPositions = brokerAdapter.isAuthenticated();
        Set<String> heldCodes = hasPositions
                ? brokerAdapter.getPositions().stream().map(Position::getStockCode).collect(Collectors.toSet())
                : Set.of();
        List<PredictionCandidateItemDto> ranked = rankTopCandidates(batch);
        List<TradingCandidateDto> items = new ArrayList<>();
        for (int index = 0; index < ranked.size(); index++) {
            PredictionCandidateItemDto item = ranked.get(index);
            items.add(TradingCandidateDto.builder()
                    .stockCode(item.getStockCode()).stockName(item.getStockName())
                    .effectiveModelScore(item.getEffectiveModelScore())
                    .effectiveEstimatedNetReturn(item.getEffectiveEstimatedNetReturn())
                    .rank(index + 1).held(heldCodes.contains(item.getStockCode())).build());
        }
        return TradingCandidateListResponseDto.builder()
                .predictionBatchId(batch.getBatchId()).modelVersion(batch.getModelVersion())
                .baseTradeDate(batch.getBaseTradeDate()).targetTradeDate(batch.getTargetTradeDate())
                .informationCutoffAt(batch.getInformationCutoffAt())
                .positionSnapshotAvailable(hasPositions).items(items).build();
    }

    /**
     * 执行上午分阶段卖出。
     *
     * @param taskEnabled 当前上午卖出任务是否启用
     * @return 本次卖出执行批次结果
     */
    public TradeExecutionBatchResponseDto executeMorningSell(BooleanSupplier taskEnabled) {
        requireTradingSession(MORNING_START, MORNING_END);
        String batchId = executionBatchService.begin("MORNING_SELL", LocalDate.now(), null).getBatchId();
        try {
            cancelActiveOrders("SELL", null);
            List<Position> positions = brokerAdapter.getPositions().stream()
                    .filter(item -> item.getAvailableQuantity() != null && item.getAvailableQuantity() > 0).toList();
            ExecutorService executor = Executors.newFixedThreadPool(Math.max(1, Math.min(4, positions.size())));
            try {
                List<TradeExecutionResponseDto> results = positions.stream()
                        .map(position -> CompletableFuture.supplyAsync(
                                () -> sellByStage(position.getStockCode(), taskEnabled), executor))
                        .toList().stream().map(CompletableFuture::join).toList();
                return completeBatch(batchId, results, "上午分阶段卖出结束");
            } finally {
                executor.shutdown();
            }
        } catch (RuntimeException exception) {
            executionBatchService.fail(batchId, exception.getMessage());
            throw exception;
        }
    }

    /**
     * 执行下午模型前三名竞争买入。
     *
     * @param taskEnabled 当前下午买入任务是否启用
     * @return 本次买入执行批次结果
     */
    public TradeExecutionBatchResponseDto executeAfternoonBuy(BooleanSupplier taskEnabled) {
        requireTradingSession(AFTERNOON_START, AFTERNOON_END);
        PredictionCandidateBatchDto batch = loadTodayBatch();
        List<PredictionCandidateItemDto> candidates = rankTopCandidates(batch);
        String batchId = executionBatchService.begin("AFTERNOON_BUY", LocalDate.now(), batch.getBatchId()).getBatchId();
        ReentrantLock executionRight = new ReentrantLock(true);
        AtomicReference<String> lockedStock = new AtomicReference<>();
        ExecutorService executor = Executors.newFixedThreadPool(candidates.size());
        try {
            cancelActiveOrders("BUY", null);
            List<TradeExecutionResponseDto> results = candidates.stream()
                    .map(candidate -> CompletableFuture.supplyAsync(
                            () -> competeAndBuy(candidate, taskEnabled, executionRight, lockedStock), executor))
                    .toList().stream().map(CompletableFuture::join).filter(item -> item != null).toList();
            return completeBatch(batchId, results, "下午模型前三名竞争买入结束");
        } catch (RuntimeException exception) {
            executionBatchService.fail(batchId, exception.getMessage());
            throw exception;
        } finally {
            executor.shutdown();
        }
    }

    private TradeExecutionResponseDto sellByStage(String stockCode, BooleanSupplier taskEnabled) {
        OrderResult last = OrderResult.fail("卖出窗口结束仍有未成交数量");
        while (LocalTime.now().isBefore(MORNING_END)) {
            if (!taskEnabled.getAsBoolean()) {
                cancelActiveOrders("SELL", stockCode);
                return toExecution(OrderResult.fail("上午卖出任务已关闭"));
            }
            int quantity = sellableQuantity(stockCode);
            if (quantity <= 0) {
                return toExecution(last.isSuccess() ? last : filledResult(stockCode, "SELL"));
            }
            last = submitStageOrder("SELL", stockCode, quantity, morningLevel(LocalTime.now()));
            awaitAndCancelRemainder(last, taskEnabled);
        }
        cancelActiveOrders("SELL", stockCode);
        return toExecution(last);
    }

    private TradeExecutionResponseDto competeAndBuy(PredictionCandidateItemDto candidate,
            BooleanSupplier taskEnabled, ReentrantLock executionRight, AtomicReference<String> lockedStock) {
        String stockCode = candidate.getStockCode();
        OrderResult last = null;
        while (LocalTime.now().isBefore(AFTERNOON_END)) {
            String locked = lockedStock.get();
            if (locked != null && !locked.equals(stockCode)) return null;
            if (!taskEnabled.getAsBoolean()) {
                if (executionRight.tryLock()) {
                    try { cancelActiveOrders("BUY", null); } finally { executionRight.unlock(); }
                }
                return toExecution(OrderResult.fail("下午买入任务已关闭"));
            }
            if (!executionRight.tryLock()) {
                sleepOneSecond();
                continue;
            }
            try {
                locked = lockedStock.get();
                if (locked != null && !locked.equals(stockCode)) return null;
                if (!taskEnabled.getAsBoolean()) {
                    cancelActiveOrders("BUY", null);
                    return toExecution(OrderResult.fail("下午买入任务已关闭"));
                }
                FiveLevelQuoteDto quote = brokerAdapter.getFiveLevelQuote(stockCode);
                TradingPriceLevel level = afternoonLevel(LocalTime.now());
                BigDecimal price = level == TradingPriceLevel.BEST_FIVE_IOC ? quote.getAsk5() : level.resolve(quote);
                int quantity = maximumBuyQuantity(brokerAdapter.getAccountInfo(), price);
                if (quantity < LOT_SIZE) {
                    return toExecution(last == null ? OrderResult.fail("可用资金不足一手") : last);
                }
                last = submitStageOrder("BUY", stockCode, quantity, level, quote);
                OrderStatus status = awaitAndCancelRemainder(last, taskEnabled);
                if (filledQuantity(last.getOrderId()) > 0) lockedStock.compareAndSet(null, stockCode);
                if (status == OrderStatus.FILLED) return toExecution(last);
            } finally {
                executionRight.unlock();
            }
        }
        return last == null ? null : toExecution(last);
    }

    private OrderResult submitStageOrder(String direction, String stockCode, int quantity, TradingPriceLevel level) {
        return submitStageOrder(direction, stockCode, quantity, level, brokerAdapter.getFiveLevelQuote(stockCode));
    }

    private OrderResult submitStageOrder(String direction, String stockCode, int quantity,
            TradingPriceLevel level, FiveLevelQuoteDto quote) {
        if (level != TradingPriceLevel.BEST_FIVE_IOC) {
            return brokerAdapter.submitOrder(direction, stockCode, level.resolve(quote), quantity, BrokerOrderType.LIMIT);
        }
        try {
            return brokerAdapter.submitOrder(direction, stockCode, null, quantity, BrokerOrderType.BEST_FIVE_IOC);
        } catch (UnsupportedOperationException exception) {
            BigDecimal price = "SELL".equals(direction) ? quote.getBid5() : quote.getAsk5();
            log.warn("券商不支持最优五档委托，改用保护限价: direction={}, stockCode={}, price={}",
                    direction, stockCode, price);
            return brokerAdapter.submitOrder(direction, stockCode, price, quantity, BrokerOrderType.LIMIT);
        }
    }

    private OrderStatus awaitAndCancelRemainder(OrderResult result, BooleanSupplier taskEnabled) {
        if (!result.isSuccess() || result.getOrderId() == null) return OrderStatus.REJECTED;
        OrderStatus status = orderPoller.waitForComplete(result.getOrderId(), taskEnabled);
        if (status == OrderStatus.FILLED) {
            result.setStatus(status);
            result.setFillTime(LocalDateTime.now());
            result.setMessage("分阶段委托已全部成交");
            return status;
        }
        OrderStatus terminal = orderPoller.cancelAndAwaitTerminal(result.getOrderId());
        result.setStatus(terminal);
        result.setMessage(terminal == OrderStatus.FILLED ? "撤单确认前已成交" : "未成交部分已撤销");
        return terminal;
    }

    private void cancelActiveOrders(String direction, String stockCode) {
        for (BrokerOrderSnapshot order : brokerAdapter.getTodayOrderSnapshots()) {
            if (!direction.equals(order.getDirection()) || stockCode != null && !stockCode.equals(order.getStockCode())) continue;
            if (order.getStatus() == OrderStatus.SUBMITTED || order.getStatus() == OrderStatus.PARTIAL
                    || order.getStatus() == OrderStatus.UNKNOWN) {
                orderPoller.cancelAndAwaitTerminal(order.getOrderId());
            }
        }
    }

    private int sellableQuantity(String stockCode) {
        return brokerAdapter.getPositions().stream().filter(item -> stockCode.equals(item.getStockCode()))
                .map(Position::getAvailableQuantity).filter(value -> value != null).findFirst().orElse(0);
    }

    private int filledQuantity(String orderId) {
        if (orderId == null) return 0;
        return brokerAdapter.getTodayOrderSnapshots().stream().filter(item -> orderId.equals(item.getOrderId()))
                .map(BrokerOrderSnapshot::getFilledQuantity).filter(value -> value != null).findFirst().orElse(0);
    }

    private int maximumBuyQuantity(AccountStatus account, BigDecimal price) {
        if (account == null || account.getAvailableCash() == null || price == null || price.signum() <= 0) return 0;
        BigDecimal available = account.getAvailableCash();
        int quantity = available.divide(price, 0, RoundingMode.DOWN).intValue() / LOT_SIZE * LOT_SIZE;
        while (quantity >= LOT_SIZE) {
            BigDecimal amount = price.multiply(BigDecimal.valueOf(quantity));
            if (amount.add(brokerAdapter.calculateFee(amount)).compareTo(available) <= 0) return quantity;
            quantity -= LOT_SIZE;
        }
        return 0;
    }

    private PredictionCandidateBatchDto loadTodayBatch() {
        PredictionCandidateBatchDto batch = predictionCandidateQueryService.getLatestUsableBatch(LocalDate.now());
        if (!LocalDate.now().equals(batch.getTargetTradeDate())) {
            throw new IllegalStateException("模型预测目标交易日与当天不一致: " + batch.getTargetTradeDate());
        }
        return batch;
    }

    private List<PredictionCandidateItemDto> rankTopCandidates(PredictionCandidateBatchDto batch) {
        List<PredictionCandidateItemDto> items = batch.getItems().stream()
                .filter(item -> item.getEffectiveModelScore() != null)
                .sorted(Comparator.comparing(PredictionCandidateItemDto::getEffectiveModelScore).reversed())
                .limit(TOP_CANDIDATE_LIMIT).toList();
        if (items.isEmpty()) throw new IllegalStateException("当日预测批次没有有效模型评分: " + batch.getBatchId());
        return items;
    }

    private void requireTradingSession(LocalTime start, LocalTime end) {
        brokerAdapter.ensureAuthenticated();
        tradingCalendarService.requireTradingDay(LocalDate.now());
        LocalTime now = LocalTime.now();
        if (now.isBefore(start) || !now.isBefore(end)) {
            throw new IllegalStateException("当前时间不在交易任务窗口: " + start + "-" + end);
        }
    }

    private TradingPriceLevel morningLevel(LocalTime time) {
        if (time.isBefore(LocalTime.of(10, 15))) return TradingPriceLevel.ASK_THREE;
        if (time.isBefore(LocalTime.of(10, 50))) return TradingPriceLevel.ASK_ONE;
        if (time.isBefore(LocalTime.of(11, 15))) return TradingPriceLevel.BID_ONE;
        return TradingPriceLevel.BEST_FIVE_IOC;
    }

    private TradingPriceLevel afternoonLevel(LocalTime time) {
        if (time.isBefore(LocalTime.of(13, 45))) return TradingPriceLevel.BID_THREE;
        if (time.isBefore(LocalTime.of(14, 20))) return TradingPriceLevel.BID_ONE;
        if (time.isBefore(LocalTime.of(14, 45))) return TradingPriceLevel.ASK_ONE;
        return TradingPriceLevel.BEST_FIVE_IOC;
    }

    private TradeExecutionBatchResponseDto completeBatch(String batchId, List<TradeExecutionResponseDto> results, String message) {
        int successCount = (int) results.stream().filter(TradeExecutionResponseDto::isSuccess).count();
        executionBatchService.complete(batchId, results.size(), successCount, message);
        return TradeExecutionBatchResponseDto.builder().executionBatchId(batchId).items(results)
                .successCount(successCount).message(message).build();
    }

    private OrderResult filledResult(String stockCode, String direction) {
        return OrderResult.builder().success(true).stockCode(stockCode).direction(direction)
                .status(OrderStatus.FILLED).message("可交易数量已全部成交").fillTime(LocalDateTime.now()).build();
    }

    private TradeExecutionResponseDto toExecution(OrderResult result) {
        return TradeExecutionResponseDto.builder()
                .success(result.isSuccess() && result.getStatus() == OrderStatus.FILLED)
                .orderId(result.getOrderId()).stockCode(result.getStockCode()).direction(result.getDirection())
                .price(result.getPrice()).quantity(result.getQuantity()).status(result.getStatus())
                .message(result.getMessage()).submitTime(result.getSubmitTime()).fillTime(result.getFillTime()).build();
    }

    private void sleepOneSecond() {
        try {
            Thread.sleep(1000L);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("交易候选竞争被中断", exception);
        }
    }
}
// AI_GENERATE_END-----------------------------
