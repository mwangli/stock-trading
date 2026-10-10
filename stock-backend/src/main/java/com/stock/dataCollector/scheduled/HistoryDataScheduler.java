package com.stock.dataCollector.scheduled;

import com.stock.dataCollector.persistence.StockInfoRepository;
import com.stock.dataCollector.service.StockDataService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 历史数据增量同步定时任务
 * <p>
 * 负责在交易日收盘后，基于已有的历史数据做轻量增量同步，
 * 仅拉取每只股票最新一段缺失的历史行情，避免每次全量扫描。
 * </p>
 *
 * @author AI Assistant
 * @since 1.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HistoryDataScheduler {

    private final StockInfoRepository stockInfoRepository;
    private final StockDataService stockDataService;

    /**
     * 兼容保留类，历史数据增量同步逻辑已迁移至 DataSyncScheduler，
     * 并通过 JobSchedulerService 统一调度。
     * 如需使用原生 @Scheduled 方式，可在此类中重新启用相关方法。
     */
}

