// AI_GENERATE_START--- ---
package com.stock.dataCollector.support;

import com.stock.dataCollector.domain.entity.StockInfo;
import com.stock.dataCollector.service.StockDataService;
import com.stock.tradingExecutor.service.SystemRuntimeConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 应用启动后数据初始化监听器。
 * 根据系统运行配置决定是否执行股票基础数据初始化。
 *
 * @author mwangli
 * @since 2026-03-10
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ApplicationStartupListener {

    private final StockDataService stockDataService;
    private final SystemRuntimeConfigService runtimeConfigService;

    /**
     * 应用就绪后检查并补充股票基础数据。
     *
     * 历史行情和新闻统一交由 Quartz 增量任务处理，启动阶段不再执行全量同步。
     */
    @Async
    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        log.info("========== 应用启动完成，开始检查股票基础数据 ==========");
        if (!runtimeConfigService.isStartupDataSyncEnabled()) {
            log.info("系统配置已关闭启动数据初始化，跳过股票列表同步");
            return;
        }
        try {
            long currentCount = stockDataService.count();
            log.info("当前 StockInfo 表中的股票数据数量: {}", currentCount);
            if (currentCount < 4000) {
                log.warn("数据量不足 4000 条（{} 条），开始执行股票列表同步", currentCount);
                StockDataService.SyncResult result = stockDataService.syncStockList();
                log.info("数据同步结果: 总数={}, 新增={}, 更新={}, 失败={}, 耗时={}ms",
                        result.getTotalCount(), result.getSavedCount(), result.getUpdatedCount(),
                        result.getFailedCount(), result.getCostTimeMs());
                List<StockInfo> sampleStocks = stockDataService.findAllStocks();
                int printCount = Math.min(5, sampleStocks.size());
                for (int i = 0; i < printCount; i++) {
                    StockInfo stock = sampleStocks.get(i);
                    log.info("股票数据示例 - 代码: {}, 名称: {}, 市场: {}, 当前价格: {}, 涨跌幅: {}, "
                                    + "涨跌额: {}, 总市值: {}, 换手率: {}, 量比: {}, 行业代码: {}",
                            stock.getCode(), stock.getName(), stock.getMarket(), stock.getPrice(),
                            stock.getChangePercent(), stock.getChangeAmount(), stock.getTotalMarketValue(),
                            stock.getTurnoverRate(), stock.getVolumeRatio(), stock.getIndustryCode());
                }
            } else {
                log.info("数据量充足（{} 条），跳过股票列表同步", currentCount);
            }
        } catch (Exception exception) {
            log.error("后台数据初始化任务（股票列表同步）失败", exception);
        }
    }
}
// AI_GENERATE_END--- ----
