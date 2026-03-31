package com.stock.tradingExecutor.job;

import com.stock.tradingExecutor.service.HistoryOrderSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 历史订单同步定时任务
 * <p>
 * 说明：本类由动态任务调度中心 {@link JobSchedulerService}
 * 通过 JobConfig 中的 beanName + methodName 反射调用，无需使用 @Scheduled 注解。
 * </p>
 *
 * @author mwangli
 * @since 2026-03-31
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HistoryOrderSyncJob {

    private final HistoryOrderSyncService historyOrderSyncService;

    /**
     * 执行历史订单全量同步
     * 同步过去3年的订单数据
     */
    public void syncHistoryOrders() {
        log.info("========== [历史订单同步任务] 开始执行全量历史订单同步 ==========");

        try {
            HistoryOrderSyncService.SyncResult result = historyOrderSyncService.syncAllHistoryOrders();

            log.info("[历史订单同步任务] 同步完成: 总获取={}, 新增={}, 重复={}, 失败={}, 批次={}, 耗时={}ms",
                    result.totalFetched(),
                    result.savedCount(),
                    result.duplicateCount(),
                    result.failedCount(),
                    result.syncBatchNo(),
                    result.costTimeMs());

        } catch (Exception e) {
            log.error("[历史订单同步任务] 同步失败: {}", e.getMessage(), e);
            throw new RuntimeException("历史订单同步任务失败: " + e.getMessage(), e);
        }

        log.info("========== [历史订单同步任务] 历史订单同步结束 ==========");
    }

    /**
     * 执行增量同步（保留接口，后续可扩展）
     */
    public void syncHistoryOrdersIncremental() {
        log.info("========== [历史订单同步任务] 开始执行增量历史订单同步 ==========");

        try {
            HistoryOrderSyncService.SyncResult result = historyOrderSyncService.syncAllHistoryOrders();

            log.info("[历史订单同步任务] 增量同步完成: 总获取={}, 新增={}, 重复={}, 失败={}, 批次={}, 耗时={}ms",
                    result.totalFetched(),
                    result.savedCount(),
                    result.duplicateCount(),
                    result.failedCount(),
                    result.syncBatchNo(),
                    result.costTimeMs());

        } catch (Exception e) {
            log.error("[历史订单同步任务] 增量同步失败: {}", e.getMessage(), e);
            throw new RuntimeException("历史订单增量同步任务失败: " + e.getMessage(), e);
        }

        log.info("========== [历史订单同步任务] 增量历史订单同步结束 ==========");
    }
}
