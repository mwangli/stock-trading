// AI_GENERATE_START --
package com.stock.tradingExecutor.job;

import com.stock.tradingExecutor.domain.dto.BrokerSyncResultDto;
import com.stock.tradingExecutor.service.BrokerDataSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 券商只读事实同步调度入口。
 * 方法保持无参 public 形式，由统一 JobSchedulerService 反射调用。
 *
 * @author mwangli
 * @since 2026-09-25
 */
@Slf4j
@Component("brokerDataSyncJob")
@RequiredArgsConstructor
public class BrokerDataSyncJob {

    private final BrokerDataSyncService brokerDataSyncService;

    /**
     * 同步当前账户、持仓、当日委托和成交。
     */
    public void syncCurrentFacts() {
        BrokerSyncResultDto result = brokerDataSyncService.syncCurrentFacts();
        log.info("[BrokerDataSyncJob] 当前事实同步完成: success={}, received={}, inserted={}, updated={}, skipped={}",
                result.isSuccess(), result.getReceivedCount(), result.getInsertedCount(),
                result.getUpdatedCount(), result.getSkippedRecordCount());
    }

    /**
     * 按月同步最近五年历史委托和成交。
     */
    public void syncLastFiveYears() {
        BrokerSyncResultDto result = brokerDataSyncService.syncLastFiveYears();
        log.info("[BrokerDataSyncJob] 五年历史同步完成: success={}, windows={}, failed={}, received={}",
                result.isSuccess(), result.getTotalWindows(), result.getFailedWindows(), result.getReceivedCount());
    }
}
// AI_GENERATE_END --
