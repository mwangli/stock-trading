// AI_GENERATE_START ------
package com.stock.dataCollector.service;

import com.stock.dataCollector.domain.entity.StockInfo;
import com.stock.dataCollector.domain.entity.StockNews;
import com.stock.dataCollector.domain.enums.NewsSyncStatus;
import com.stock.dataCollector.persistence.NewsRepository;
import com.stock.dataCollector.persistence.StockInfoRepository;
import com.stock.dataCollector.support.SecuritiesClient;
import com.stock.tradingExecutor.runtime.service.SystemRuntimeConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.BulkOperations;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import jakarta.annotation.PreDestroy;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 股票新闻采集服务
 * 从证券平台 weixin.citicsinfo.com 采集股票相关新闻，用于情感分析等下游任务
 * 支持多线程并行采集与 MongoDB 批量写入
 *
 * @author mwangli
 * @since 2026-03-14
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StockNewsService {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter DATES_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");
    /** 每页条数，50 条降低采集负担与限流风险 */
    private static final int PAGE_SIZE = 50;
    /** 每只股票每个来源（新闻/公告）最多采集页数，历史新闻无意义 */
    private static final int MAX_PAGES = 1;
    /** 单条详情请求间隔，降低限流风险 */
    private static final long REQUEST_DELAY_MS = 150;
    /** 并行采集线程数 */
    private static final int PARALLEL_THREADS = 6;
    /** 批量写入阈值，与每页 50 条对齐，一次写入整页 */
    private static final int BATCH_INSERT_SIZE = 50;

    /** 新闻发布时间保留天数，0 表示不按时间清理。 */
    @Value("${app.runtime.news.retention-days:365}")
    private int newsRetentionDays;

    /** 每只股票最多保留的新闻和公告数量，0 表示不按数量清理。 */
    @Value("${app.runtime.news.max-records-per-stock:300}")
    private int maxNewsRecordsPerStock;

    /** 采集来源：新闻(20002)、公告(20001) */
    private static final List<NewsSource> NEWS_SOURCES = List.of(
            new NewsSource(SecuritiesClient.MENU_ID_NEWS, "新闻"),
            new NewsSource(SecuritiesClient.MENU_ID_ANNOUNCEMENT, "公告")
    );

    private final SecuritiesClient securitiesClient;
    private final NewsRepository newsRepository;
    private final StockInfoRepository stockInfoRepository;
    private final MongoTemplate mongoTemplate;
    private final SystemRuntimeConfigService runtimeConfigService;

    private final ExecutorService executor = Executors.newFixedThreadPool(PARALLEL_THREADS,
            r -> {
                Thread t = new Thread(r, "news-collect-" + r.hashCode() % 100);
                t.setDaemon(false);
                return t;
            });

    /** 代码→名称全局缓存，lazy 加载，避免重复 findAll */
    private volatile Map<String, String> codeToNameCache = null;

    @PreDestroy
    public void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 采集指定股票的新闻和公告（单只调用，使用全局缓存）。
     *
     * @param stockCode 股票代码，如 600519
     * @return 本次采集新增的新闻数量
     * @throws IllegalStateException 新闻或公告来源未全部同步成功时抛出
     */
    public int collectStockNewsByCode(String stockCode) {
        runtimeConfigService.requireNewsCollectionEnabled();
        String batchId = UUID.randomUUID().toString();
        StockCollectResult result = collectStockNewsByCode(stockCode, getCodeToNameCache(), batchId);
        if (!result.isFullySuccessful()) {
            throw new IllegalStateException("股票 " + stockCode + " 新闻同步未完整成功，状态=" + result.status());
        }
        return result.savedCount();
    }

    /**
     * 采集指定股票的新闻和公告，并在 MySQL 中记录本次同步水位。
     *
     * @param stockCode 股票代码
     * @param codeToNameCache 代码到名称缓存
     * @param batchId 批次 ID
     * @return 单只股票采集结果
     */
    private StockCollectResult collectStockNewsByCode(String stockCode, Map<String, String> codeToNameCache,
                                                       String batchId) {
        String stockName = resolveStockName(stockCode, codeToNameCache);
        String label = stockCode + (stockName != null && !stockName.isEmpty() ? " " + stockName : "");
        StockInfo stockInfo = markSyncStarted(stockCode, batchId);

        long existingCount = newsRepository.countByStockCode(stockCode);
        log.info("开始采集股票 {} 的新闻与公告（当前 {} 条，batchId={}）", label, existingCount, batchId);
        int totalSaved = 0;
        int successfulSources = 0;
        List<String> errorCodes = new ArrayList<>();
        int sourceTotal = NEWS_SOURCES.size();

        for (int i = 0; i < sourceTotal; i++) {
            NewsSource src = NEWS_SOURCES.get(i);
            log.info("股票 {} 开始采集 {}/{} {}", label, i + 1, sourceTotal, src.category);
            try {
                int saved = collectBySource(stockCode, stockName, label, src, i + 1, sourceTotal);
                totalSaved += saved;
                successfulSources++;
            } catch (Exception e) {
                String errorCode = "NEWS_SOURCE_" + src.menuId + "_FAILED";
                errorCodes.add(errorCode);
                log.error("股票 {} {} 来源同步失败，errorCode={}: {}", label, src.category,
                        errorCode, e.getMessage(), e);
            }
        }

        try {
            cleanupStockNews(stockCode, label);
        } catch (Exception e) {
            log.warn("股票 {} 新闻历史清理失败，不影响本次同步水位判定: {}", label, e.getMessage(), e);
        }

        NewsSyncStatus status;
        if (successfulSources == sourceTotal) {
            status = totalSaved > 0 ? NewsSyncStatus.SUCCESS_WITH_CHANGE : NewsSyncStatus.SUCCESS_NO_CHANGE;
        } else if (successfulSources > 0) {
            status = NewsSyncStatus.PARTIAL;
        } else {
            status = NewsSyncStatus.FAILED;
        }
        String errorCode = errorCodes.isEmpty() ? null : String.join(",", errorCodes);
        updateSyncWatermark(stockInfo, status, totalSaved, errorCode);
        return new StockCollectResult(stockCode, totalSaved, status, errorCode);
    }

    /**
     * 按采集来源（新闻/公告）采集，批量写入
     *
     * @param sourceIndex 当前来源序号，如 1
     * @param sourceTotal 来源总数，如 2
     */
    private int collectBySource(String stockCode, String stockName, String label, NewsSource src,
                                int sourceIndex, int sourceTotal) {
        List<StockNews> batch = new ArrayList<>(BATCH_INSERT_SIZE);
        int totalSaved = 0;
        int totalSkipped = 0;
        int page = 1;
        boolean hasMore = true;
        Set<String> seenIdsInRun = new HashSet<>();

        while (hasMore && page <= MAX_PAGES) {
            Map<String, Object> listResp = securitiesClient.requestNewsList(stockCode, page, PAGE_SIZE, src.menuId);
            Object grid0Obj = listResp.get("GRID0");
            if (!(grid0Obj instanceof List<?> grid0)) {
                throw new IllegalStateException("证券平台" + src.category + "列表缺少 GRID0");
            }

            if (grid0.isEmpty()) {
                log.info("股票 {} {} 第 {} 页无数据，跳过", label, src.category, page);
                hasMore = false;
                break;
            }

            // 方案 A：批量预查已存在的 externalId，将 N 次 exists 合并为 1 次 in 查询
            List<String> externalIds = new ArrayList<>();
            List<String> titles = new ArrayList<>();
            List<String> dateStrs = new ArrayList<>();
            for (Object item : grid0) {
                if (!(item instanceof String row)) {
                    continue;
                }
                String[] parts = row.split("\\|", -1);
                if (parts.length < 3) {
                    continue;
                }
                String externalId = parts[0].trim();
                String title = parts[1].trim();
                String dateStr = parts[2].trim();
                if (externalId.isEmpty() || title.isEmpty()) {
                    continue;
                }
                externalIds.add(externalId);
                titles.add(title);
                dateStrs.add(dateStr);
            }

            String titlePreview = titles.stream().limit(5)
                    .map(t -> t.length() > 25 ? t.substring(0, 25) + "…" : t)
                    .collect(Collectors.joining(" | "));
            log.info("股票 {} {} 第 {} 页: 采集到 {} 条标题，示例: {}", label, src.category, page, titles.size(), titlePreview);

            Set<String> existingIds = externalIds.isEmpty() ? Set.of()
                    : newsRepository.findExternalIdsByStockCodeAndExternalIdIn(stockCode, externalIds).stream()
                            .map(StockNews::getExternalId)
                            .filter(Objects::nonNull)
                            .collect(Collectors.toSet());
            int skipCount = (int) externalIds.stream().filter(existingIds::contains).count();
            totalSkipped += skipCount;

            for (int i = 0; i < externalIds.size(); i++) {
                String externalId = externalIds.get(i);
                if (existingIds.contains(externalId) || !seenIdsInRun.add(externalId)) {
                    log.debug("{} externalId={} 已存在，跳过", src.category, externalId);
                    continue;
                }

                StockNews news = fetchDetail(externalId, titles.get(i), dateStrs.get(i), stockCode, stockName, src);
                if (news != null) {
                    batch.add(news);
                    if (batch.size() >= BATCH_INSERT_SIZE) {
                        totalSaved += batchInsert(batch, src.category);
                        batch.clear();
                    }
                }
                sleep(REQUEST_DELAY_MS);
            }

            if (grid0.size() < PAGE_SIZE || page >= MAX_PAGES) {
                hasMore = false;
            } else {
                page++;
                sleep(REQUEST_DELAY_MS);
            }
        }

        if (!batch.isEmpty()) {
            totalSaved += batchInsert(batch, src.category);
        }

        log.info("股票 {} {} 采集完成，新增 {} 条，已存在跳过 {} 条（单股进度 {}/{}）", label, src.category, totalSaved, totalSkipped, sourceIndex, sourceTotal);
        return totalSaved;
    }

    /**
     * 批量插入 MongoDB
     */
    private int batchInsert(List<StockNews> batch, String category) {
        if (batch.isEmpty()) {
            return 0;
        }
        StockNews first = batch.get(0);
        String label = first.getStockCode() + (first.getStockName() != null && !first.getStockName().isEmpty()
                ? " " + first.getStockName() : "");
        try {
            BulkOperations bulkOps = mongoTemplate.bulkOps(BulkOperations.BulkMode.UNORDERED, StockNews.class);
            for (StockNews news : batch) {
                bulkOps.insert(news);
            }
            bulkOps.execute();
            log.info("股票 {} {} 批量写入 {} 条", label, category, batch.size());
            return batch.size();
        } catch (Exception e) {
            log.warn("股票 {} {} 批量写入失败，回退逐条保存: {}", label, category, e.getMessage());
            int savedCount = 0;
            for (StockNews news : batch) {
                try {
                    newsRepository.save(news);
                    savedCount++;
                } catch (Exception ex) {
                    log.warn("保存失败 externalId={}: {}", news.getExternalId(), ex.getMessage());
                }
            }
            log.info("股票 {} {} 逐条回退实际写入 {} 条", label, category, savedCount);
            if (savedCount != batch.size()) {
                throw new IllegalStateException("MongoDB 批量及逐条回退写入均未完整成功");
            }
            return savedCount;
        }
    }

    /**
     * 清理指定股票的过期新闻，并限制单股历史记录数量，避免增量采集造成 MongoDB 无限增长。
     */
    private void cleanupStockNews(String stockCode, String label) {
        long deletedByAge = 0;
        long deletedByLimit = 0;

        if (newsRetentionDays > 0) {
            LocalDateTime cutoff = LocalDateTime.now().minusDays(newsRetentionDays);
            Query expiredQuery = Query.query(Criteria.where("stockCode").is(stockCode)
                    .and("publishTime").lt(cutoff));
            deletedByAge = mongoTemplate.remove(expiredQuery, StockNews.class).getDeletedCount();
        }

        if (maxNewsRecordsPerStock > 0) {
            Query stockQuery = Query.query(Criteria.where("stockCode").is(stockCode));
            long currentCount = mongoTemplate.count(stockQuery, StockNews.class);
            if (currentCount > maxNewsRecordsPerStock) {
                int excessCount = Math.toIntExact(Math.min(
                        currentCount - maxNewsRecordsPerStock, Integer.MAX_VALUE));
                Query excessQuery = Query.query(Criteria.where("stockCode").is(stockCode))
                        .with(Sort.by(Sort.Direction.ASC, "publishTime"))
                        .limit(excessCount);
                excessQuery.fields().include("id");
                List<String> excessIds = mongoTemplate.find(excessQuery, StockNews.class).stream()
                        .map(StockNews::getId)
                        .filter(Objects::nonNull)
                        .toList();
                if (!excessIds.isEmpty()) {
                    deletedByLimit = mongoTemplate.remove(
                            Query.query(Criteria.where("_id").in(excessIds)), StockNews.class)
                            .getDeletedCount();
                }
            }
        }

        if (deletedByAge > 0 || deletedByLimit > 0) {
            log.info("股票 {} 新闻数据清理完成: 按时间删除 {} 条，按数量删除 {} 条，保留上限 {} 条/股",
                    label, deletedByAge, deletedByLimit, maxNewsRecordsPerStock);
        }
    }

    /**
     * 批量采集所有股票的新闻（多线程并行）。
     *
     * @param maxStocks 最多采集的股票数量，0 表示不限制
     * @return 采集结果统计
     */
    public CollectResult collectAllStockNews(int maxStocks) {
        runtimeConfigService.requireNewsCollectionEnabled();
        String batchId = UUID.randomUUID().toString();
        log.info("开始批量采集股票新闻（{} 线程并行），最多 {} 只股票，batchId={}",
                PARALLEL_THREADS, maxStocks <= 0 ? "全部" : maxStocks, batchId);
        List<String> codes = stockInfoRepository.findAllCodes();
        if (codes == null || codes.isEmpty()) {
            log.warn("未找到任何股票代码，跳过新闻采集");
            return new CollectResult(batchId, 0, 0, 0, 0, 0, 0);
        }

        int limit = maxStocks > 0 ? Math.min(maxStocks, codes.size()) : codes.size();
        Map<String, String> codeToName = getCodeToNameCache();

        AtomicInteger totalSaved = new AtomicInteger(0);
        AtomicInteger processedCount = new AtomicInteger(0);
        AtomicInteger withChangeCount = new AtomicInteger(0);
        AtomicInteger noChangeCount = new AtomicInteger(0);
        AtomicInteger partialCount = new AtomicInteger(0);
        AtomicInteger failedCount = new AtomicInteger(0);
        CountDownLatch latch = new CountDownLatch(limit);
        long startMs = System.currentTimeMillis();

        for (int i = 0; i < limit; i++) {
            String code = codes.get(i);
            executor.submit(() -> {
                try {
                    StockCollectResult result = collectStockNewsByCode(code, codeToName, batchId);
                    totalSaved.addAndGet(result.savedCount());
                    switch (result.status()) {
                        case SUCCESS_WITH_CHANGE -> withChangeCount.incrementAndGet();
                        case SUCCESS_NO_CHANGE -> noChangeCount.incrementAndGet();
                        case PARTIAL -> partialCount.incrementAndGet();
                        case FAILED -> failedCount.incrementAndGet();
                        default -> failedCount.incrementAndGet();
                    }
                    log.info("股票 {} 采集完成（状态={}，本股新增 {} 条）",
                            resolveStockLabel(code, codeToName), result.status(), result.savedCount());
                } catch (Exception e) {
                    failedCount.incrementAndGet();
                    log.error("股票 {} 新闻采集失败: {}", resolveStockLabel(code, codeToName), e.getMessage(), e);
                } finally {
                    int processed = processedCount.incrementAndGet();
                    if (processed % 20 == 0 || processed == limit) {
                        log.info("新闻采集整体进度: {}/{} 只股票，累计新增 {} 条，部分失败 {} 只，失败 {} 只",
                                processed, limit, totalSaved.get(), partialCount.get(), failedCount.get());
                    }
                    latch.countDown();
                }
            });
        }

        try {
            boolean completed = latch.await(24, TimeUnit.HOURS);
            if (!completed) {
                throw new IllegalStateException("新闻采集等待超过 24 小时，仍有任务未完成");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("新闻采集被中断", e);
        }

        long costMs = System.currentTimeMillis() - startMs;
        log.info("批量新闻采集完成: batchId={}，处理 {} 只，新增 {} 条，有新增 {} 只，无新增 {} 只，部分失败 {} 只，失败 {} 只，耗时 {} 分钟",
                batchId, processedCount.get(), totalSaved.get(), withChangeCount.get(), noChangeCount.get(),
                partialCount.get(), failedCount.get(), costMs / 60_000);
        return new CollectResult(batchId, processedCount.get(), totalSaved.get(), withChangeCount.get(),
                noChangeCount.get(), partialCount.get(), failedCount.get());
    }

    /**
     * 拉取新闻/公告详情（不写入，由调用方批量写入）
     */
    private StockNews fetchDetail(String externalId, String listTitle, String dateStr,
                                  String stockCode, String stockName, NewsSource src) {
        Map<String, Object> detailResp = securitiesClient.requestNewsDetail(externalId, src.menuId);
        if (detailResp.isEmpty()) {
            return null;
        }

        Object grid0Obj = detailResp.get("GRID0");
        String content = "";
        if (grid0Obj instanceof List<?> grid0 && !grid0.isEmpty() && grid0.get(0) instanceof String c) {
            content = c.trim();
        }

        String title = getString(detailResp, "TITLE", listTitle);
        String dates = getString(detailResp, "DATES", "");
        String time = getString(detailResp, "TIME", "00:00:00");
        String source = getString(detailResp, "MEDIA", "");

        LocalDateTime publishTime = parsePublishTime(dateStr, dates, time);

        StockNews news = new StockNews();
        news.setExternalId(externalId);
        news.setTitle(title);
        news.setContent(content);
        news.setStockCode(stockCode);
        news.setStockName(stockName != null && !stockName.isEmpty() ? stockName : null);
        news.setSource(source.isEmpty() ? null : source);
        news.setCategory(src.category);
        news.setPublishTime(publishTime);
        news.setCreateTime(LocalDateTime.now());
        return news;
    }

    private String getString(Map<String, Object> map, String key, String defaultValue) {
        Object v = map.get(key);
        return v != null ? v.toString().trim() : defaultValue;
    }

    private LocalDateTime parsePublishTime(String dateStr, String dates, String time) {
        try {
            if (dateStr != null && !dateStr.isEmpty()) {
                return LocalDateTime.parse(dateStr, DATE_FORMAT);
            }
            if (dates != null && !dates.isEmpty() && time != null && !time.isEmpty()) {
                LocalDate d = LocalDate.parse(dates, DATES_FORMAT);
                LocalTime t = LocalTime.parse(time, DateTimeFormatter.ofPattern("HH:mm:ss"));
                return LocalDateTime.of(d, t);
            }
        } catch (Exception e) {
            log.debug("解析发布时间失败: dateStr={}, dates={}, time={}", dateStr, dates, time, e);
        }
        return LocalDateTime.now();
    }

    /**
     * 将股票新闻同步状态更新为运行中。
     *
     * @param stockCode 股票代码
     * @param batchId 批次 ID
     * @return 已持久化的股票实体
     */
    private StockInfo markSyncStarted(String stockCode, String batchId) {
        StockInfo stockInfo = stockInfoRepository.findByCode(stockCode)
                .orElseThrow(() -> new IllegalArgumentException("股票代码不存在: " + stockCode));
        stockInfo.setNewsLastAttemptAt(LocalDateTime.now());
        stockInfo.setNewsSyncBatchId(batchId);
        stockInfo.setNewsSyncStatus(NewsSyncStatus.RUNNING);
        stockInfo.setNewsSyncErrorCode(null);
        return stockInfoRepository.save(stockInfo);
    }

    /**
     * 在 MongoDB 处理完成后更新 MySQL 新闻同步水位。
     *
     * @param stockInfo 股票实体
     * @param status 最终同步状态
     * @param savedCount 本次新增数量
     * @param errorCode 标准错误码
     */
    private void updateSyncWatermark(StockInfo stockInfo, NewsSyncStatus status, int savedCount, String errorCode) {
        LocalDateTime completedAt = LocalDateTime.now();
        newsRepository.findFirstByStockCodeOrderByPublishTimeDesc(stockInfo.getCode())
                .map(StockNews::getPublishTime)
                .ifPresent(stockInfo::setNewsLastPublishTime);
        stockInfo.setNewsSyncStatus(status);
        stockInfo.setNewsSyncErrorCode(errorCode);

        if (status == NewsSyncStatus.SUCCESS_WITH_CHANGE || status == NewsSyncStatus.SUCCESS_NO_CHANGE) {
            stockInfo.setNewsLastSuccessAt(completedAt);
            int previousNoChangeCount = stockInfo.getNewsConsecutiveNoChangeCount() == null
                    ? 0 : stockInfo.getNewsConsecutiveNoChangeCount();
            stockInfo.setNewsConsecutiveNoChangeCount(savedCount > 0 ? 0 : previousNoChangeCount + 1);
            stockInfo.setNewsNextSyncAt(completedAt.plusDays(1));
        } else {
            stockInfo.setNewsNextSyncAt(completedAt.plusMinutes(15));
        }

        try {
            stockInfoRepository.save(stockInfo);
        } catch (Exception e) {
            log.error("股票 {} 新闻 MongoDB 处理完成，但 MySQL 水位更新失败，batchId={}",
                    stockInfo.getCode(), stockInfo.getNewsSyncBatchId(), e);
            throw new IllegalStateException("新闻同步水位更新失败", e);
        }
    }

    private void sleep(long ms) {
        try {
            TimeUnit.MILLISECONDS.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("新闻采集延迟被中断");
        }
    }

    /**
     * 获取代码→名称缓存，lazy 加载，线程安全
     */
    private Map<String, String> getCodeToNameCache() {
        Map<String, String> cache = codeToNameCache;
        if (cache == null) {
            synchronized (this) {
                cache = codeToNameCache;
                if (cache == null) {
                    cache = stockInfoRepository.findAll().stream()
                            .filter(s -> s.getCode() != null)
                            .collect(Collectors.toMap(s -> s.getCode(), s -> s.getName() != null ? s.getName() : "", (a, b) -> a));
                    codeToNameCache = cache;
                    log.debug("股票代码→名称缓存已加载，共 {} 条", cache.size());
                }
            }
        }
        return cache;
    }

    /**
     * 失效代码→名称缓存，股票列表同步后调用以刷新
     */
    public void invalidateCodeToNameCache() {
        codeToNameCache = null;
    }

    /**
     * 解析股票展示标签，用于日志输出
     *
     * @param stockCode       股票代码
     * @param codeToNameCache 缓存，为 null 时按需查询
     * @return "代码 名称" 或仅 "代码"（名称不存在时）
     */
    private String resolveStockLabel(String stockCode, Map<String, String> codeToNameCache) {
        String name = resolveStockName(stockCode, codeToNameCache);
        return stockCode + (name != null && !name.isEmpty() ? " " + name : "");
    }

    /**
     * 解析股票名称，用于冗余存储与日志
     *
     * @param codeToNameCache 缓存，为 null 时按需查询
     */
    private String resolveStockName(String stockCode, Map<String, String> codeToNameCache) {
        if (codeToNameCache != null) {
            String name = codeToNameCache.get(stockCode);
            return (name != null && !name.isEmpty()) ? name : null;
        }
        return stockInfoRepository.findByCode(stockCode)
                .map(s -> s.getName())
                .orElse(null);
    }

    /**
     * 新闻采集批次结果。
     *
     * @param batchId 批次 ID
     * @param processedCount 已处理股票数量
     * @param savedCount 新增新闻数量
     * @param withChangeCount 有新增股票数量
     * @param noChangeCount 无新增股票数量
     * @param partialCount 部分失败股票数量
     * @param failedCount 失败股票数量
     */
    public record CollectResult(String batchId, int processedCount, int savedCount, int withChangeCount,
                                int noChangeCount, int partialCount, int failedCount) {}

    /** 单只股票采集结果。 */
    private record StockCollectResult(String stockCode, int savedCount, NewsSyncStatus status, String errorCode) {

        /**
         * 判断新闻和公告两个来源是否均同步成功。
         *
         * @return 两个来源均成功时返回 true
         */
        private boolean isFullySuccessful() {
            return status == NewsSyncStatus.SUCCESS_WITH_CHANGE || status == NewsSyncStatus.SUCCESS_NO_CHANGE;
        }
    }

    /** 采集来源（新闻/公告）。 */
    private record NewsSource(String menuId, String category) {}
}
// AI_GENERATE_END ------
