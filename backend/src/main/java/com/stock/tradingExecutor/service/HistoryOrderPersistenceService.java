package com.stock.tradingExecutor.service;

import com.stock.tradingExecutor.domain.entity.HistoryOrder;
import com.stock.tradingExecutor.persistence.HistoryOrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 历史订单持久化服务
 * 使用JPA Repository的saveAll实现批量插入
 *
 * @author mwangli
 * @since 2026-04-01
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HistoryOrderPersistenceService {

    private final HistoryOrderRepository historyOrderRepository;

    private static final int BATCH_SIZE = 100;

    /**
     * 批量保存订单（使用JPA saveAll + 批量查重）
     * 先批量查重再插入，避免逐条查询
     *
     * @param orders  订单列表
     * @param batchNo 批次号
     * @return 保存结果
     */
    @Transactional
    public SyncPageResult saveOrders(List<HistoryOrder> orders, String batchNo) {
        if (orders.isEmpty()) {
            return new SyncPageResult(0, 0, 0);
        }

        long startTime = System.currentTimeMillis();
        LocalDateTime now = LocalDateTime.now();

        Set<String> existingOrderNos = new HashSet<>(historyOrderRepository.findExistingOrderNos(
                orders.stream().map(HistoryOrder::getOrderNo).toList()
        ));
        int duplicateCount = existingOrderNos.size();
        log.info("[历史订单持久化] 批量去重检查完成, 重复: {}", duplicateCount);

        List<HistoryOrder> toSave = new ArrayList<>();
        for (HistoryOrder order : orders) {
            if (!existingOrderNos.contains(order.getOrderNo())) {
                order.setSyncBatchNo(batchNo);
                order.setLastSyncTime(now);
                order.setCreateTime(now);
                order.setUpdateTime(now);
                toSave.add(order);
            }
        }

        int savedCount = 0;
        int failedCount = 0;

        for (int i = 0; i < toSave.size(); i += BATCH_SIZE) {
            int end = Math.min(i + BATCH_SIZE, toSave.size());
            List<HistoryOrder> batch = toSave.subList(i, end);
            try {
                historyOrderRepository.saveAll(batch);
                savedCount += batch.size();
            } catch (Exception e) {
                log.warn("[历史订单持久化] 批量保存失败: {}", e.getMessage());
                failedCount += batch.size();
            }
        }

        long costTime = System.currentTimeMillis() - startTime;
        log.info("[历史订单持久化] 批量写入完成, 新增: {}, 重复: {}, 失败: {}, 耗时: {}ms", savedCount, duplicateCount, failedCount, costTime);

        return new SyncPageResult(savedCount, duplicateCount, failedCount);
    }

    public record SyncPageResult(int saved, int duplicate, int failed) {}
}