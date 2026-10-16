// AI_GENERATE_START ---------------
package com.stock.tradingExecutor.service;

import com.stock.tradingExecutor.domain.dto.BrokerHistoryQueryRequest;
import com.stock.tradingExecutor.domain.dto.BrokerSyncResultDto;
import com.stock.tradingExecutor.domain.entity.BrokerSyncBatchEntity;
import com.stock.tradingExecutor.domain.entity.OrderStatus;
import com.stock.tradingExecutor.domain.entity.Position;
import com.stock.tradingExecutor.domain.vo.AccountStatus;
import com.stock.tradingExecutor.domain.vo.BrokerOrderSnapshot;
import com.stock.tradingExecutor.execution.BrokerAdapter;
import com.stock.tradingExecutor.execution.TradingProperties;
import com.stock.tradingExecutor.sync.SyncWindow;
import com.stock.tradingExecutor.sync.SyncWindowExecutor;
import com.stock.tradingExecutor.sync.SyncWindowResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

/**
 * 券商只读事实同步编排服务。
 * 负责当前账户事实同步、历史月份切分、批次续传和幂等写入。
 *
 * @author mwangli
 * @since 2026-09-25
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BrokerDataSyncService {

    private static final int MAX_MANUAL_WINDOW_DAYS = 31;
    private static final int MAX_HISTORY_YEARS = 3;
    private static final String ACCOUNT = "ACCOUNT";
    private static final String POSITION = "POSITION";
    private static final String TODAY_ORDER = "TODAY_ORDER";
    private static final String HISTORY_ORDER = "HISTORY_ORDER";

    private final ReentrantLock syncLock = new ReentrantLock();
    private final BrokerAdapter brokerAdapter;
    private final BrokerFactPersistenceService persistenceService;
    private final TradeRecordAggregationService tradeRecordAggregationService;
    private final PerformanceReportService performanceReportService;
    private final TradingProperties tradingProperties;
    private final SyncWindowExecutor syncWindowExecutor;
    private volatile String activeOperation;

    /**
     * 同步当前账户、持仓和当日已成交委托。
     *
     * @return 三类当前事实的汇总结果
     */
    public BrokerSyncResultDto syncCurrentFacts() {
        return executeExclusively("CURRENT_FACTS", this::doSyncCurrentFacts);
    }

    private BrokerSyncResultDto doSyncCurrentFacts() {
        ensureAuthenticated();
        LocalDate today = LocalDate.now();
        List<BatchExecution> executions = new ArrayList<>();

        executions.add(runBatch(ACCOUNT, today, today, false, () -> {
            AccountStatus account = brokerAdapter.getAccountInfo();
            if (account == null) {
                throw new IllegalStateException("券商账户资金为空");
            }
            persistenceService.saveAccountSnapshot(account, "CURRENT_SYNC");
            return new BrokerFactPersistenceService.PersistenceResult(1, 1, 0, 0);
        }));

        executions.add(runBatch(POSITION, today, today, false, () -> {
            List<Position> positions = brokerAdapter.getPositions();
            int saved = persistenceService.savePositionSnapshot(positions, "CURRENT_SYNC");
            return new BrokerFactPersistenceService.PersistenceResult(
                    positions.size(), saved, 0, positions.size() - saved);
        }));

        executions.add(runBatch(TODAY_ORDER, today, today, false, () -> {
            List<BrokerOrderSnapshot> orders = brokerAdapter.getTodayOrderSnapshots().stream()
                    .filter(this::isFilledOrder)
                    .toList();
            return persistenceService.upsertOrders(orders);
        }));

        BrokerSyncResultDto result = aggregate("CURRENT_FACTS", today, today, executions);
        assembleTradeRecords();
        return result;
    }

    /**
     * 同步指定日期窗口的已成交历史委托。
     * 手动查询窗口最多为 31 个自然日，成功窗口也会重新查询并执行幂等更新。
     *
     * @param request 日期范围
     * @return 已成交历史委托同步结果
     */
    public BrokerSyncResultDto syncHistoryWindow(BrokerHistoryQueryRequest request) {
        return executeExclusively("HISTORY_WINDOW", () -> {
            validateManualWindow(request);
            ensureAuthenticated();
            BrokerSyncResultDto result = syncHistoryOrdersWindow(request.getStartDate(), request.getEndDate(), false);
            assembleTradeRecords();
            return result;
        });
    }

    /**
     * 按自然月同步最近三年已成交历史委托。
     * 已成功的月份直接跳过，失败或部分成功月份会在下次运行时重试。
     *
     * @return 最近三年历史同步汇总结果
     */
    public BrokerSyncResultDto syncHistoryBackfill() {
        return executeExclusively("HISTORY_BACKFILL", this::doSyncHistoryBackfill);
    }

    private BrokerSyncResultDto doSyncHistoryBackfill() {
        ensureAuthenticated();
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusYears(MAX_HISTORY_YEARS);
        LocalDate cursor = startDate;
        List<BatchExecution> executions = new ArrayList<>();

        while (!cursor.isAfter(endDate)) {
            LocalDate monthEnd = cursor.with(TemporalAdjusters.lastDayOfMonth());
            if (monthEnd.isAfter(endDate)) {
                monthEnd = endDate;
            }
            BrokerSyncResultDto windowResult = syncHistoryOrdersWindow(cursor, monthEnd, true);
            executions.add(BatchExecution.fromAggregate(windowResult));
            cursor = monthEnd.plusDays(1);
            pauseBetweenMonths();
        }
        BrokerSyncResultDto result = aggregate("HISTORY_BACKFILL", startDate, endDate, executions);
        assembleTradeRecords();
        return result;
    }

    private BrokerSyncResultDto executeExclusively(String operation, Supplier<BrokerSyncResultDto> action) {
        if (!syncLock.tryLock()) {
            throw new IllegalStateException("已有券商同步任务正在运行: " + activeOperation);
        }
        activeOperation = operation;
        try {
            log.info("[BrokerSync] 获得单实例同步执行权: operation={}", operation);
            return action.get();
        } finally {
            activeOperation = null;
            syncLock.unlock();
            log.info("[BrokerSync] 释放单实例同步执行权: operation={}", operation);
        }
    }

    private BrokerSyncResultDto syncHistoryOrdersWindow(LocalDate startDate, LocalDate endDate,
                                                          boolean skipSuccessful) {
        List<BatchExecution> executions = new ArrayList<>();
        executions.add(runBatch(HISTORY_ORDER, startDate, endDate, skipSuccessful, () -> {
            List<BrokerOrderSnapshot> orders = brokerAdapter.getHistoryOrderSnapshots(startDate, endDate).stream()
                    .filter(this::isFilledOrder)
                    .toList();
            return persistenceService.upsertOrders(orders);
        }));

        return aggregate("HISTORY_WINDOW", startDate, endDate, executions);
    }

    private boolean isFilledOrder(BrokerOrderSnapshot order) {
        return order != null
                && order.getStatus() == OrderStatus.FILLED
                && order.getFilledQuantity() != null
                && order.getFilledQuantity() > 0
                && order.getAverageFillPrice() != null
                && order.getAverageFillPrice().signum() > 0;
    }

    /**
     * 根据当前账户全部已成交历史订单重新组装交易记录。
     * 该方法独立于券商订单同步，可重复调用且不会产生重复数据。
     *
     * @return 交易记录组装结果
     */
    public TradeRecordAggregationService.AggregationResult assembleTradeRecords() {
        int removedInvalidOrders = persistenceService.removeInvalidFilledOrders();
        if (removedInvalidOrders > 0) {
            log.info("[BrokerSync] 已清理无效成交委托: count={}", removedInvalidOrders);
        }
        TradeRecordAggregationService.AggregationResult result =
                tradeRecordAggregationService.rebuildCurrentAccountTradeRecords();
        log.info("[BrokerSync] 交易记录组装完成: records={}, skippedOrders={}, unmatchedSellQuantity={}",
                result.recordCount(), result.skippedOrderCount(), result.unmatchedSellQuantity());
        performanceReportService.refreshDailySnapshot();
        return result;
    }

    private BatchExecution runBatch(String syncType, LocalDate startDate, LocalDate endDate,
                                    boolean skipSuccessful,
                                    Supplier<BrokerFactPersistenceService.PersistenceResult> action) {
        SyncWindow window = new SyncWindow(syncType, startDate, endDate);
        BrokerSyncBatchEntity batch = skipSuccessful
                ? null
                : persistenceService.beginBatch(syncType, startDate, endDate);
        boolean skipped = skipSuccessful && persistenceService.isWindowSuccessful(syncType, startDate, endDate);
        SyncWindowResult<BrokerFactPersistenceService.PersistenceResult> result = syncWindowExecutor.execute(
                window,
                skipped,
                action,
                value -> persistenceService.completeBatch(batch.getId(), value),
                exception -> {
                    persistenceService.failBatch(batch.getId(), exception);
                    log.error("[BrokerSync] 同步窗口失败: type={}, start={}, end={}, reason={}",
                            syncType, startDate, endDate, exception.getMessage());
                });
        return switch (result.status()) {
            case SUCCESS -> BatchExecution.success(result.value());
            case SKIPPED -> BatchExecution.skipped();
            case FAILED -> BatchExecution.failed();
        };
    }
    private BrokerSyncResultDto aggregate(String operation, LocalDate startDate, LocalDate endDate,
                                          List<BatchExecution> executions) {
        int successfulWindows = executions.stream().mapToInt(BatchExecution::successfulWindows).sum();
        int skippedWindows = executions.stream().mapToInt(BatchExecution::skippedWindows).sum();
        int failedWindows = executions.stream().mapToInt(BatchExecution::failedWindows).sum();
        int received = executions.stream().mapToInt(BatchExecution::received).sum();
        int inserted = executions.stream().mapToInt(BatchExecution::inserted).sum();
        int updated = executions.stream().mapToInt(BatchExecution::updated).sum();
        int skippedRecords = executions.stream().mapToInt(BatchExecution::skippedRecords).sum();
        boolean success = failedWindows == 0;
        return BrokerSyncResultDto.builder()
                .operation(operation)
                .startDate(startDate)
                .endDate(endDate)
                .totalWindows(successfulWindows + skippedWindows + failedWindows)
                .successfulWindows(successfulWindows)
                .skippedWindows(skippedWindows)
                .failedWindows(failedWindows)
                .receivedCount(received)
                .insertedCount(inserted)
                .updatedCount(updated)
                .skippedRecordCount(skippedRecords)
                .success(success)
                .message(success ? "同步完成" : "部分同步窗口失败，请查看同步批次")
                .build();
    }

    private void validateManualWindow(BrokerHistoryQueryRequest request) {
        if (request == null || request.getStartDate() == null || request.getEndDate() == null) {
            throw new IllegalArgumentException("开始日期和结束日期不能为空");
        }
        if (request.getStartDate().isAfter(request.getEndDate())) {
            throw new IllegalArgumentException("开始日期不能晚于结束日期");
        }
        long days = ChronoUnit.DAYS.between(request.getStartDate(), request.getEndDate()) + 1;
        if (days > MAX_MANUAL_WINDOW_DAYS) {
            throw new IllegalArgumentException("单次历史同步不能超过31个自然日");
        }
    }

    private void ensureAuthenticated() {
        brokerAdapter.ensureAuthenticated();
    }

    private void pauseBetweenMonths() {
        try {
            Thread.sleep(Math.max(0, tradingProperties.getHistoryMonthIntervalMs()));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("历史同步被中断", exception);
        }
    }

    private record BatchExecution(int successfulWindows, int skippedWindows, int failedWindows,
                                  int received, int inserted, int updated, int skippedRecords) {

        private static BatchExecution success(BrokerFactPersistenceService.PersistenceResult result) {
            return new BatchExecution(1, 0, 0, result.received(), result.inserted(),
                    result.updated(), result.skipped());
        }

        private static BatchExecution skipped() {
            return new BatchExecution(0, 1, 0, 0, 0, 0, 0);
        }

        private static BatchExecution failed() {
            return new BatchExecution(0, 0, 1, 0, 0, 0, 0);
        }

        private static BatchExecution fromAggregate(BrokerSyncResultDto result) {
            return new BatchExecution(
                    result.getSuccessfulWindows(), result.getSkippedWindows(), result.getFailedWindows(),
                    result.getReceivedCount(), result.getInsertedCount(), result.getUpdatedCount(),
                    result.getSkippedRecordCount());
        }
    }
}
// AI_GENERATE_END ---------------