// AI_GENERATE_START ---
package com.stock.tradingExecutor.service;

import com.stock.tradingExecutor.domain.entity.AccountSnapshotEntity;
import com.stock.tradingExecutor.domain.entity.BrokerFillEntity;
import com.stock.tradingExecutor.domain.entity.BrokerOrderEntity;
import com.stock.tradingExecutor.domain.entity.BrokerSyncBatchEntity;
import com.stock.tradingExecutor.domain.entity.Position;
import com.stock.tradingExecutor.domain.entity.PositionSnapshotEntity;
import com.stock.tradingExecutor.domain.entity.PositionSnapshotItemEntity;
import com.stock.tradingExecutor.domain.vo.AccountStatus;
import com.stock.tradingExecutor.domain.vo.BrokerFillSnapshot;
import com.stock.tradingExecutor.domain.vo.BrokerOrderSnapshot;
import com.stock.tradingExecutor.persistence.AccountSnapshotRepository;
import com.stock.tradingExecutor.persistence.BrokerFillRepository;
import com.stock.tradingExecutor.persistence.BrokerOrderRepository;
import com.stock.tradingExecutor.persistence.BrokerSyncBatchRepository;
import com.stock.tradingExecutor.persistence.PositionSnapshotItemRepository;
import com.stock.tradingExecutor.persistence.PositionSnapshotRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 券商只读事实持久化服务。
 * 负责账户和持仓快照落库，以及委托、成交和同步批次的幂等更新。
 *
 * @author mwangli
 * @since 2026-09-25
 */
@Service
@RequiredArgsConstructor
public class BrokerFactPersistenceService {

    private final AccountSnapshotRepository accountSnapshotRepository;
    private final PositionSnapshotRepository positionSnapshotRepository;
    private final PositionSnapshotItemRepository positionSnapshotItemRepository;
    private final BrokerOrderRepository brokerOrderRepository;
    private final BrokerFillRepository brokerFillRepository;
    private final BrokerSyncBatchRepository brokerSyncBatchRepository;
    private final BrokerIdentityService brokerIdentityService;

    /**
     * 保存账户资金快照。
     *
     * @param account 账户资金
     * @param source  快照来源
     * @return 快照编号
     */
    @Transactional
    public String saveAccountSnapshot(AccountStatus account, String source) {
        LocalDateTime capturedAt = LocalDateTime.now();
        String snapshotId = UUID.randomUUID().toString();
        AccountSnapshotEntity entity = new AccountSnapshotEntity()
                .setSnapshotId(snapshotId)
                .setBrokerCode(brokerIdentityService.getBrokerCode())
                .setAccountId(brokerIdentityService.getAccountId())
                .setTotalAssets(account.getTotalAssets())
                .setAvailableCash(account.getAvailableCash())
                .setFrozenCash(account.getFrozenAmount())
                .setMarketValue(account.getTotalPosition())
                .setCapturedAt(capturedAt)
                .setSource(source);
        accountSnapshotRepository.save(entity);
        return snapshotId;
    }

    /**
     * 保存完整持仓快照。
     * 每次同步创建新快照，不覆盖历史持仓事实。
     *
     * @param positions 当前持仓
     * @param source    快照来源
     * @return 实际保存的持仓明细数量
     */
    @Transactional
    public int savePositionSnapshot(List<Position> positions, String source) {
        LocalDateTime capturedAt = LocalDateTime.now();
        String snapshotId = UUID.randomUUID().toString();
        PositionSnapshotEntity snapshot = new PositionSnapshotEntity()
                .setSnapshotId(snapshotId)
                .setBrokerCode(brokerIdentityService.getBrokerCode())
                .setAccountId(brokerIdentityService.getAccountId())
                .setCapturedAt(capturedAt)
                .setSource(source);
        positionSnapshotRepository.save(snapshot);

        List<PositionSnapshotItemEntity> items = new ArrayList<>();
        for (Position position : positions) {
            if (position.getStockCode() == null || position.getStockCode().isBlank()) {
                continue;
            }
            items.add(new PositionSnapshotItemEntity()
                    .setSnapshotId(snapshotId)
                    .setStockCode(position.getStockCode())
                    .setStockName(position.getStockName())
                    .setMarket(position.getMarket())
                    .setShareholderAccount(brokerIdentityService.hashSensitiveValue(position.getShareholderAccount()))
                    .setTotalQuantity(defaultInteger(position.getQuantity()))
                    .setAvailableQuantity(defaultInteger(position.getAvailableQuantity()))
                    .setFrozenQuantity(defaultInteger(position.getFrozenQuantity()))
                    .setAverageCost(position.getAvgCost())
                    .setCurrentPrice(position.getCurrentPrice())
                    .setMarketValue(position.getMarketValue())
                    .setFirstBuyDate(position.getBuyDate())
                    .setLastBuyDate(position.getBuyDate())
                    .setCapturedAt(capturedAt));
        }
        positionSnapshotItemRepository.saveAll(items);
        return items.size();
    }

    /**
     * 幂等保存券商委托。
     *
     * @param orders 标准化委托快照
     * @return 持久化统计
     */
    @Transactional
    public PersistenceResult upsertOrders(List<BrokerOrderSnapshot> orders) {
        int inserted = 0;
        int updated = 0;
        int skipped = 0;
        String brokerCode = brokerIdentityService.getBrokerCode();
        String accountId = brokerIdentityService.getAccountId();

        for (BrokerOrderSnapshot order : orders) {
            if (!isValidOrder(order)) {
                skipped++;
                continue;
            }
            LocalDate tradeDate = order.getOrderTime().toLocalDate();
            BrokerOrderEntity entity = brokerOrderRepository
                    .findByBrokerCodeAndAccountIdAndTradeDateAndBrokerOrderNo(
                            brokerCode, accountId, tradeDate, order.getOrderId())
                    .orElse(null);
            boolean isNew = entity == null;
            if (isNew) {
                entity = new BrokerOrderEntity()
                        .setBrokerCode(brokerCode)
                        .setAccountId(accountId)
                        .setBrokerOrderNo(order.getOrderId())
                        .setAttemptNo(1)
                        .setTradeDate(tradeDate);
            }

            String resolvedStatus = resolveOrderStatus(entity.getStatus(),
                    order.getStatus() != null ? order.getStatus().getCode() : "UNKNOWN");
            int resolvedFilledQuantity = Math.max(
                    defaultInteger(entity.getFilledQuantity()), defaultInteger(order.getFilledQuantity()));
            entity.setStockCode(order.getStockCode())
                    .setStockName(order.getStockName())
                    .setMarket(order.getMarket())
                    .setSide(order.getDirection())
                    .setStatus(resolvedStatus)
                    .setRawStatus(order.getRawStatus())
                    .setOrderPrice(order.getOrderPrice())
                    .setOrderQuantity(order.getOrderQuantity())
                    .setFilledQuantity(resolvedFilledQuantity)
                    .setAverageFillPrice(order.getAverageFillPrice() != null
                            ? order.getAverageFillPrice() : entity.getAverageFillPrice())
                    .setSubmittedAt(order.getOrderTime())
                    .setLastQueriedAt(LocalDateTime.now());
            if (isFinalStatus(resolvedStatus) && entity.getTerminalAt() == null) {
                entity.setTerminalAt(LocalDateTime.now());
            }
            brokerOrderRepository.save(entity);
            if (isNew) {
                inserted++;
            } else {
                updated++;
            }
        }
        return new PersistenceResult(orders.size(), inserted, updated, skipped);
    }

    /**
     * 幂等保存券商成交。
     * 无成交编号时使用稳定哈希作为合成键。
     *
     * @param fills 标准化成交快照
     * @return 持久化统计
     */
    @Transactional
    public PersistenceResult upsertFills(List<BrokerFillSnapshot> fills) {
        int inserted = 0;
        int updated = 0;
        int skipped = 0;
        String brokerCode = brokerIdentityService.getBrokerCode();
        String accountId = brokerIdentityService.getAccountId();

        for (BrokerFillSnapshot fill : fills) {
            if (!isValidFill(fill)) {
                skipped++;
                continue;
            }
            boolean synthetic = fill.getFillId() == null || fill.getFillId().isBlank();
            String fillNo = synthetic ? buildSyntheticFillKey(brokerCode, accountId, fill) : fill.getFillId();
            BrokerFillEntity entity = brokerFillRepository
                    .findByBrokerCodeAndAccountIdAndBrokerFillNo(brokerCode, accountId, fillNo)
                    .orElse(null);
            boolean isNew = entity == null;
            if (isNew) {
                entity = new BrokerFillEntity()
                        .setBrokerCode(brokerCode)
                        .setAccountId(accountId)
                        .setBrokerFillNo(fillNo);
            }

            LocalDate tradeDate = fill.getFillTime().toLocalDate();
            Long brokerOrderId = findBrokerOrderId(
                    brokerCode, accountId, tradeDate, fill.getOrderId());
            entity.setBrokerOrderId(brokerOrderId)
                    .setBrokerOrderNo(fill.getOrderId())
                    .setSyntheticKey(synthetic)
                    .setStockCode(fill.getStockCode())
                    .setStockName(fill.getStockName())
                    .setSide(fill.getDirection())
                    .setFillPrice(fill.getFillPrice())
                    .setFillQuantity(fill.getFillQuantity())
                    .setFillAmount(defaultAmount(fill))
                    .setTradeDate(tradeDate)
                    .setFilledAt(fill.getFillTime());
            brokerFillRepository.save(entity);
            if (isNew) {
                inserted++;
            } else {
                updated++;
            }
        }
        return new PersistenceResult(fills.size(), inserted, updated, skipped);
    }

    /**
     * 创建或重置同步窗口批次。
     *
     * @param syncType   同步类型
     * @param windowStart 窗口开始日期
     * @param windowEnd  窗口结束日期
     * @return 运行中的批次
     */
    @Transactional
    public BrokerSyncBatchEntity beginBatch(String syncType, LocalDate windowStart, LocalDate windowEnd) {
        String brokerCode = brokerIdentityService.getBrokerCode();
        String accountId = brokerIdentityService.getAccountId();
        BrokerSyncBatchEntity batch = brokerSyncBatchRepository
                .findByBrokerCodeAndAccountIdAndSyncTypeAndWindowStartAndWindowEnd(
                        brokerCode, accountId, syncType, windowStart, windowEnd)
                .orElseGet(() -> new BrokerSyncBatchEntity()
                        .setBatchId(UUID.randomUUID().toString())
                        .setBrokerCode(brokerCode)
                        .setAccountId(accountId)
                        .setSyncType(syncType)
                        .setWindowStart(windowStart)
                        .setWindowEnd(windowEnd));
        batch.setStatus("RUNNING")
                .setRequestCount(defaultInteger(batch.getRequestCount()) + 1)
                .setReceivedCount(0)
                .setInsertedCount(0)
                .setUpdatedCount(0)
                .setSkippedCount(0)
                .setLastError(null)
                .setStartedAt(LocalDateTime.now())
                .setFinishedAt(null);
        return brokerSyncBatchRepository.save(batch);
    }

    /**
     * 将同步批次标记为成功或部分成功。
     *
     * @param batchId 批次数据库主键
     * @param result  持久化统计
     * @return 完成后的批次
     */
    @Transactional
    public BrokerSyncBatchEntity completeBatch(Long batchId, PersistenceResult result) {
        BrokerSyncBatchEntity batch = requireBatch(batchId);
        batch.setReceivedCount(result.received())
                .setInsertedCount(result.inserted())
                .setUpdatedCount(result.updated())
                .setSkippedCount(result.skipped())
                .setStatus(result.skipped() > 0 ? "PARTIAL_SUCCESS" : "SUCCESS")
                .setCursorValue(batch.getWindowEnd() != null ? batch.getWindowEnd().toString() : "CURRENT")
                .setFinishedAt(LocalDateTime.now());
        return brokerSyncBatchRepository.save(batch);
    }

    /**
     * 将同步批次标记为失败，并只保存脱敏错误摘要。
     *
     * @param batchId  批次数据库主键
     * @param exception 同步异常
     * @return 失败后的批次
     */
    @Transactional
    public BrokerSyncBatchEntity failBatch(Long batchId, RuntimeException exception) {
        BrokerSyncBatchEntity batch = requireBatch(batchId);
        batch.setStatus("FAILED")
                .setLastError(sanitizeError(exception))
                .setFinishedAt(LocalDateTime.now());
        return brokerSyncBatchRepository.save(batch);
    }

    /**
     * 判断历史同步窗口是否已经成功完成。
     *
     * @param syncType   同步类型
     * @param windowStart 窗口开始日期
     * @param windowEnd  窗口结束日期
     * @return SUCCESS 时返回 true
     */
    @Transactional(readOnly = true)
    public boolean isWindowSuccessful(String syncType, LocalDate windowStart, LocalDate windowEnd) {
        return brokerSyncBatchRepository
                .findByBrokerCodeAndAccountIdAndSyncTypeAndWindowStartAndWindowEnd(
                        brokerIdentityService.getBrokerCode(), brokerIdentityService.getAccountId(),
                        syncType, windowStart, windowEnd)
                .map(batch -> "SUCCESS".equals(batch.getStatus()))
                .orElse(false);
    }

    private BrokerSyncBatchEntity requireBatch(Long batchId) {
        return brokerSyncBatchRepository.findById(batchId)
                .orElseThrow(() -> new IllegalStateException("同步批次不存在: " + batchId));
    }

    private Long findBrokerOrderId(String brokerCode, String accountId, LocalDate tradeDate, String orderId) {
        if (orderId == null || orderId.isBlank()) {
            return null;
        }
        return brokerOrderRepository
                .findByBrokerCodeAndAccountIdAndTradeDateAndBrokerOrderNo(
                        brokerCode, accountId, tradeDate, orderId)
                .map(BrokerOrderEntity::getId)
                .orElse(null);
    }

    private String buildSyntheticFillKey(String brokerCode, String accountId, BrokerFillSnapshot fill) {
        String material = String.join("|",
                brokerCode,
                accountId,
                defaultString(fill.getOrderId()),
                defaultString(fill.getStockCode()),
                defaultString(fill.getDirection()),
                fill.getFillPrice().toPlainString(),
                String.valueOf(fill.getFillQuantity()),
                fill.getFillTime().toString());
        return brokerIdentityService.stableKey(material);
    }

    private boolean isValidOrder(BrokerOrderSnapshot order) {
        return order != null
                && order.getOrderId() != null && !order.getOrderId().isBlank()
                && order.getOrderTime() != null
                && order.getOrderPrice() != null
                && order.getOrderQuantity() != null && order.getOrderQuantity() > 0;
    }

    private boolean isValidFill(BrokerFillSnapshot fill) {
        return fill != null
                && fill.getStockCode() != null && !fill.getStockCode().isBlank()
                && fill.getDirection() != null && !"UNKNOWN".equals(fill.getDirection())
                && fill.getFillPrice() != null
                && fill.getFillQuantity() != null && fill.getFillQuantity() > 0
                && fill.getFillTime() != null;
    }

    private BigDecimal defaultAmount(BrokerFillSnapshot fill) {
        return fill.getFillAmount() != null
                ? fill.getFillAmount()
                : fill.getFillPrice().multiply(BigDecimal.valueOf(fill.getFillQuantity()));
    }

    private Integer defaultInteger(Integer value) {
        return value != null ? value : 0;
    }

    private String resolveOrderStatus(String existingStatus, String incomingStatus) {
        if (isFinalStatus(existingStatus) && !isFinalStatus(incomingStatus)) {
            return existingStatus;
        }
        if ("UNKNOWN".equals(incomingStatus) && existingStatus != null) {
            return existingStatus;
        }
        return incomingStatus;
    }

    private boolean isFinalStatus(String status) {
        return "FILLED".equals(status) || "CANCELLED".equals(status) || "REJECTED".equals(status);
    }

    private String defaultString(String value) {
        return value != null ? value : "";
    }

    private String sanitizeError(RuntimeException exception) {
        String message = exception.getMessage() != null ? exception.getMessage() : exception.getClass().getSimpleName();
        String sanitized = message.replaceAll(
                "(?i)(token|password|account|mobile|cookie)\\s*[=:]\\s*[^,\\s]+", "$1=***");
        return sanitized.length() > 500 ? sanitized.substring(0, 500) : sanitized;
    }

    /**
     * 单类事实持久化统计。
     *
     * @param received 接收数量
     * @param inserted 新增数量
     * @param updated  更新数量
     * @param skipped  跳过数量
     */
    public record PersistenceResult(int received, int inserted, int updated, int skipped) {
    }
}
// AI_GENERATE_END ---
