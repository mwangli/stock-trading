// AI_GENERATE_START-----------------------------------------------------
package com.stock.platform.task;

import com.stock.data.dto.ExternalNewsCollectionResult;
import com.stock.data.dto.NewsCollectionBackfillResult;
import com.stock.data.dto.StockListSyncResult;
import com.stock.data.dto.TushareFreeDailyBackfillResult;

import com.stock.data.service.ExternalNewsCollectionService;
import com.stock.data.service.NewsCollectionBackfillService;
import com.stock.data.service.StockListSyncService;
import com.stock.data.service.StockQueryService;
import com.stock.data.service.StockNewsService;
import com.stock.data.service.TradingCalendarService;
import com.stock.data.service.TushareFreeDailySyncService;
import com.stock.model.service.PredictionEvaluationQueueService;
import com.stock.model.service.NewsFeatureSnapshotBackfillService;
import com.stock.model.dto.NewsFeatureSnapshotBackfillResult;
import com.stock.platform.exception.JobSkippedException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

/**
 * 数据同步定时任务
 * <p>
 * 说明：本类提供数据同步业务方法，由固定 Quartz Job 类直接调用。
 * 默认任务配置由 stock-job 模块统一维护。
 * </p>
 *
 * @author mwangli
 * @since 2026-10-29
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataSyncScheduler {

    /** 增量新闻采集每次最多处理的股票数，0 表示不限制 */
    private static final int NEWS_DAILY_MAX_STOCKS = 0;
    /** 历史回填目标交易日数量。 */
    private static final int PRICE_BACKFILL_TRADING_DAYS = 1000;

    private final StockListSyncService stockListSyncService;
    private final StockQueryService stockQueryService;
    private final StockNewsService stockNewsService;
    private final ExternalNewsCollectionService externalNewsCollectionService;
    private final NewsCollectionBackfillService newsCollectionBackfillService;
    private final NewsFeatureSnapshotBackfillService newsFeatureSnapshotBackfillService;
    private final TradingCalendarService tradingCalendarService;
    private final TushareFreeDailySyncService tushareFreeDailySyncService;
    private final PredictionEvaluationQueueService predictionEvaluationQueueService;

    /**
     * 每周同步证券 API 股票代码和名称
     * <p>
     * 对应任务: stockListSync
     * Cron: 0 0 1 * * SUN
     */
    public void syncStockListDaily() {
        log.info("========== [数据同步任务] 开始执行股票列表同步 ==========");
        try {
            StockListSyncResult result = stockListSyncService.syncStockList();
            log.info("[数据同步任务] 股票列表同步完成: 总数={}, 新增={}, 更新={}, 失败={}, 耗时={}ms",
                    result.getTotalCount(), result.getSavedCount(), result.getUpdatedCount(),
                    result.getFailedCount(), result.getCostTimeMs());

        } catch (Exception e) {
            log.error("[数据同步任务] 股票列表同步失败", e);
            throw new RuntimeException("股票列表同步失败: " + e.getMessage(), e);
        }
    }

    /**
     * 同步当前工作日的全市场日线增量。
     *
     * 每日任务按交易日一次请求全市场数据，不扫描或回补历史缺口。
     * 返回数据按当前股票池过滤后批量写入统一日线集合。
     *
     * @throws JobSkippedException 当前日期为周末时抛出
     * @throws IllegalStateException 股票池为空或同步失败时抛出
     */
    public void syncDailyStockData() {
        LocalDate today = LocalDate.now();
        if (!tradingCalendarService.isTradingDay(today)) {
            throw new JobSkippedException("当前日期为周末: " + today);
        }

        List<String> codes = stockQueryService.findAllCodes();
        if (codes == null || codes.isEmpty()) {
            throw new IllegalStateException("未找到任何股票代码，无法执行日线同步");
        }
        log.info("[数据同步任务] Tushare 全市场日线同步开始: tradeDate={}, stockCount={}",
                today, codes.size());
        TushareFreeDailyBackfillResult result = tushareFreeDailySyncService.syncTradeDate(today, codes);
        log.info("[数据同步任务] Tushare 全市场日线同步完成: tradeDate={}, requests={}, rows={}, "
                        + "upserted={}, coveredStocks={}",
                today, result.requestedDateCount(), result.returnedRowCount(),
                result.upsertedRowCount(), result.coveredStockCount());
        int evaluationQueuedCount = predictionEvaluationQueueService.resumeAvailable();
        log.info("[数据同步任务] 已唤醒模型评估队列: evaluationQueued={}", evaluationQueuedCount);
    }

    /**
     * 回补目标交易日窗口内的历史日线。
     *
     * 该任务与每日增量完全分离，按日期一次请求全市场并对目标窗口幂等重扫。
     *
     * @throws IllegalStateException 股票池为空或回填失败时抛出
     */
    public void backfillHistoricalStockData() {
        List<String> codes = stockQueryService.findAllCodes();
        if (codes == null || codes.isEmpty()) {
            throw new IllegalStateException("未找到任何股票代码，无法执行历史日线回补");
        }
        log.info("[数据同步任务] Tushare 历史日线回填开始: targetTradingDays={}, stockCount={}",
                PRICE_BACKFILL_TRADING_DAYS, codes.size());
        TushareFreeDailyBackfillResult result = tushareFreeDailySyncService.backfill(
                codes, PRICE_BACKFILL_TRADING_DAYS);
        log.info("[数据同步任务] Tushare 历史日线回填完成: range={}~{}, requests={}, rows={}, "
                        + "upserted={}, coveredStocks={}",
                result.startDate(), result.endDate(), result.requestedDateCount(),
                result.returnedRowCount(), result.upsertedRowCount(), result.coveredStockCount());
    }

    /**
     * 按当前采集治理版本回填历史新闻和事件事实。
     *
     * 任务按持久化游标分批执行，单次运行达到资源上限后保留待续跑状态。
     */
    public void backfillNewsCollectionQuality() {
        log.info("========== [数据同步任务] 开始回填新闻采集治理 ==========");
        NewsCollectionBackfillResult result = newsCollectionBackfillService.backfill();
        log.info("[数据同步任务] 新闻采集治理回填完成: version={}, scanned={}, updated={}, facts={}, "
                        + "duplicates={}, ignored={}, typeCounts={}, roleCounts={}, failed={}, "
                        + "lastProcessedId={}, completed={}",
                result.pipelineVersion(), result.scannedCount(), result.updatedCount(),
                result.eventFactCount(), result.duplicateCount(), result.ignoredCount(),
                result.documentTypeCounts(), result.documentRoleCounts(), result.failedCount(),
                result.lastProcessedId(), result.completed());
        log.info("========== [数据同步任务] 新闻采集治理回填结束 ==========");
    }

    /**
     * 按交易日分批准备 `temporal-news-v2-8d` 有事件稀疏快照和日期水位。
     *
     * @throws IllegalStateException 快照生成或审计失败时抛出
     */
    public void backfillNewsFeatureSnapshots() {
        log.info("========== [数据同步任务] 开始回填新闻日级特征快照 ==========");
        NewsFeatureSnapshotBackfillResult result = newsFeatureSnapshotBackfillService.backfill();
        log.info("[数据同步任务] 新闻特征日期准备完成: processedDates={}, generatedSnapshots={}, existingSnapshots={}, failedDates={}, lastDate={}, completed={}, audit={}",
                result.processedDateCount(), result.generatedSnapshotCount(), result.existingSnapshotCount(),
                result.failedDateCount(), result.lastProcessedDate(), result.completed(), result.audit());
        log.info("========== [数据同步任务] 新闻日级特征快照回填结束 ==========");
    }
    /**
     * 每日增量新闻采集
     * <p>
     * 对应任务: newsDailySync
     * Cron: 0 0 5 * * MON-FRI（每交易日 5:00）
     * 每只股票每来源检查最新一页，已存在则跳过详情请求，仅入库新数据并记录同步水位
     * </p>
     */
    public void collectNewsDailySync() {
        log.info("========== [数据同步任务] 开始执行每日增量新闻采集 ==========");
        try {
            StockNewsService.CollectResult result = stockNewsService.collectAllStockNews(NEWS_DAILY_MAX_STOCKS);
            log.info("[数据同步任务] 每日增量新闻采集完成: batchId={}，处理 {} 只，新增 {} 条，有新增 {} 只，无新增 {} 只，部分失败 {} 只，失败 {} 只",
                    result.batchId(), result.processedCount(), result.savedCount(), result.withChangeCount(),
                    result.noChangeCount(), result.partialCount(), result.failedCount());
            if (result.partialCount() > 0 || result.failedCount() > 0) {
                throw new IllegalStateException("每日新闻同步未完整成功: batchId=" + result.batchId()
                        + ", partial=" + result.partialCount() + ", failed=" + result.failedCount());
            }
            ExternalNewsCollectionResult externalResult = externalNewsCollectionService.collectIncremental();
            if (externalResult.enabled()) {
                log.info("[数据同步任务] 外部新闻来源采集完成: configuredSources={}, successfulSources={}, "
                                + "fetched={}, inserted={}, eventDocuments={}, errors={}",
                        externalResult.configuredSourceCount(), externalResult.successfulSourceCount(),
                        externalResult.fetchedCount(), externalResult.insertedCount(),
                        externalResult.eventFactCount(), externalResult.errors());
                if (externalResult.successfulSourceCount() < externalResult.configuredSourceCount()) {
                    throw new IllegalStateException("外部新闻来源未全部采集成功: configured="
                            + externalResult.configuredSourceCount() + ", successful="
                            + externalResult.successfulSourceCount());
                }
            }
            newsFeatureSnapshotBackfillService.prepareLatestCompletedDate();
        } catch (Exception e) {
            log.error("[数据同步任务] 每日增量新闻采集失败", e);
            throw new RuntimeException("每日增量新闻采集失败: " + e.getMessage(), e);
        }
        log.info("========== [数据同步任务] 每日增量新闻采集结束 ==========");
    }
}
// AI_GENERATE_END-----------------------------------------------------
