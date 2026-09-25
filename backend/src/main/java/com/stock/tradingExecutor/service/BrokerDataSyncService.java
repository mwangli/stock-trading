// AI_GENERATE_START ---
package com.stock.tradingExecutor.service;

import com.stock.tradingExecutor.domain.dto.BrokerHistoryQueryRequest;
import com.stock.tradingExecutor.domain.dto.BrokerSyncResultDto;
import com.stock.tradingExecutor.domain.entity.BrokerSyncBatchEntity;
import com.stock.tradingExecutor.domain.entity.Position;
import com.stock.tradingExecutor.domain.vo.AccountStatus;
import com.stock.tradingExecutor.domain.vo.BrokerFillSnapshot;
import com.stock.tradingExecutor.domain.vo.BrokerOrderSnapshot;
import com.stock.tradingExecutor.execution.BrokerAdapter;
import com.stock.tradingExecutor.execution.TradingProperties;
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
    private static final String ACCOUNT = "ACCOUNT";
    private static final String POSITION = "POSITION";
    private static final String TODAY_ORDER = "TODAY_ORDER";
    private static final String TODAY_FILL = "TODAY_FILL";
    private static final String HISTORY_ORDER = "HISTORY_ORDER";
    private static final String HISTORY_FILL = "HISTORY_FILL";

    private final ReentrantLock syncLock = new ReentrantLock();
    private final BrokerAdapter brokerAdapter;
    private final BrokerFactPersistenceService persistenceService;
    private final TradingProperties tradingProperties;
    private volatile String activeOperation;

    /**
     * 同步当前账户、持仓、当日委托和当日成交。
     *
     * @return 四类当前事实的汇总结果
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
            List<BrokerOrderSnapshot> orders = brokerAdapter.getTodayOrderSnapshots();
            return persistenceService.upsertOrders(orders);
        }));

        executions.add(runBatch(TODAY_FILL, today, today, false, () -> {
            List<BrokerFillSnapshot> fills = brokerAdapter.getTodayFillSnapshots();
            return persistenceService.upsertFills(fills);
        }));

        return aggregate("CURRENT_FACTS", today, today, executions);
    }

    /**
     * 同步指定日期窗口的历史委托和成交。
     * 手动查询窗口最多为 31 个自然日，成功窗口也会重新查询并执行幂等更新。
     *
     * @param request 日期范围
     * @return 历史委托和成交同步结果
     */
    public BrokerSyncResultDto syncHistoryWindow(BrokerHistoryQueryRequest request) {
        return executeExclusively("HISTORY_WINDOW", () -> {
            validateManualWindow(request);
            ensureAuthenticated();
            return syncHistoryWindow(request.getStartDate(), request.getEndDate(), false);
        });
    }

    /**
     * 按自然月同步最近五年历史委托和成交。
     * 已成功的月份直接跳过，失败或部分成功月份会在下次运行时重试。
     *
     * @return 五年历史同步汇总结果
     */
    public BrokerSyncResultDto syncLastFiveYears() {
        return executeExclusively("HISTORY_FIVE_YEARS", this::doSyncLastFiveYears);
    }

    private BrokerSyncResultDto doSyncLastFiveYears() {
        ensureAuthenticated();
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusYears(5);
        LocalDate cursor = startDate;
        List<BatchExecution> executions = new ArrayList<>();

        while (!cursor.isAfter(endDate)) {
            LocalDate monthEnd = cursor.with(TemporalAdjusters.lastDayOfMonth());
            if (monthEnd.isAfter(endDate)) {
                monthEnd = endDate;
            }
            BrokerSyncResultDto windowResult = syncHistoryWindow(cursor, monthEnd, true);
            executions.add(BatchExecution.fromAggregate(windowResult));
            cursor = monthEnd.plusDays(1);
            pauseBetweenMonths();
        }
        return aggregate("HISTORY_FIVE_YEARS", startDate, endDate, executions);
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

    private BrokerSyncResultDto syncHistoryWindow(LocalDate startDate, LocalDate endDate,
                                                   boolean skipSuccessful) {
        List<BatchExecution> executions = new ArrayList<>();
        executions.add(runBatch(HISTORY_ORDER, startDate, endDate, skipSuccessful, () -> {
            List<BrokerOrderSnapshot> orders = brokerAdapter.getHistoryOrderSnapshots(startDate, endDate);
            return persistenceService.upsertOrders(orders);
        }));
        executions.add(runBatch(HISTORY_FILL, startDate, endDate, skipSuccessful, () -> {
            List<BrokerFillSnapshot> fills = brokerAdapter.getHistoryFillSnapshots(startDate, endDate);
            return persistenceService.upsertFills(fills);
        }));
        return aggregate("HISTORY_WINDOW", startDate, endDate, executions);
    }

    private BatchExecution runBatch(String syncType, LocalDate startDate, LocalDate endDate,
                                    boolean skipSuccessful,
                                    Supplier<BrokerFactPersistenceService.PersistenceResult> action) {
        if (skipSuccessful && persistenceService.isWindowSuccessful(syncType, startDate, endDate)) {
            log.info("[BrokerSync] 跳过已成功窗口: type={}, start={}, end={}", syncType, startDate, endDate);
            return BatchExecution.skipped();
        }

        BrokerSyncBatchEntity batch = persistenceService.beginBatch(syncType, startDate, endDate);
        try {
            BrokerFactPersistenceService.PersistenceResult result = action.get();
            persistenceService.completeBatch(batch.getId(), result);
            return BatchExecution.success(result);
        } catch (RuntimeException exception) {
            persistenceService.failBatch(batch.getId(), exception);
            log.error("[BrokerSync] 同步窗口失败: type={}, start={}, end={}, reason={}",
                    syncType, startDate, endDate, exception.getMessage());
            return BatchExecution.failed();
        }
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
        if (!brokerAdapter.isAuthenticated()) {
            throw new IllegalStateException("券商会话无效，无法执行只读事实同步");
        }
    }

    private void pauseBetweenMonths() {
        try {
            Thread.sleep(Math.max(0, tradingProperties.getHistoryMonthIntervalMs()));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("五年历史同步被中断", exception);
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
// AI_GENERATE_END ---
