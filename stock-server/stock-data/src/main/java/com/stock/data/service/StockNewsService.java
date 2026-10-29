// AI_GENERATE_START---------------------
package com.stock.data.service;

import com.stock.data.dto.NewsEventFactSyncResult;
import com.stock.data.entity.StockInfo;
import com.stock.data.entity.StockNews;
import com.stock.data.enums.NewsSyncStatus;
import com.stock.data.repository.NewsRepository;
import com.stock.data.repository.StockInfoRepository;
import com.stock.data.client.SecuritiesClient;
import com.stock.data.validator.DelistedStockDetector;
import com.stock.data.validator.NewsContentQualityValidator;
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
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.ThreadLocalRandom;

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

    private final StockNewsDocumentFactory newsDocumentFactory;
    private final NewsCollectionPipelineService newsCollectionPipelineService;
    private final NewsEventFactService newsEventFactService;
    private final StockNewsSyncStateService syncStateService;
    private final NewsContentQualityValidator contentQualityValidator;

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

    private final ExecutorService executor = Executors.newFixedThreadPool(PARALLEL_THREADS,
            r -> {
                Thread t = new Thread(r, "news-collect-" + r.hashCode() % 100);
                t.setDaemon(false);
                return t;
            });

    /**
     * 从信息表中随机读取一条包含标题或正文的新闻样本。
     *
     * @return 可用于单条情感验证的随机新闻；无可用数据时返回 empty
     */
    public Optional<StockNews> findRandomNewsSample() {
        Criteria usableContent = new Criteria().orOperator(
                Criteria.where("title").exists(true).ne(""),
                Criteria.where("content").exists(true).ne(""));
        Query query = Query.query(usableContent)
                .with(Sort.by(Sort.Direction.DESC, "publishTime"))
                .limit(50);
        List<StockNews> samples = mongoTemplate.find(query, StockNews.class, "stock_news");
        if (samples.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(samples.get(ThreadLocalRandom.current().nextInt(samples.size())));
    }

    /** 关闭新闻采集线程池并停止接收新任务。 */
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
     * @return 本次新增或修复的新闻数量
     * @throws IllegalStateException 新闻或公告来源未全部同步成功时抛出
     */
    public int collectStockNewsByCode(String stockCode) {
        StockInfo stockInfo = stockInfoRepository.findByCode(stockCode)
                .orElseThrow(() -> new IllegalArgumentException("股票代码不存在: " + stockCode));
        if (DelistedStockDetector.isDelisted(stockInfo)) {
            log.warn("跳过退市/ST 股票新闻采集: stockCode={}, name={}", stockCode, stockInfo.getName());
            return 0;
        }
        String batchId = UUID.randomUUID().toString();
        StockCollectResult result = collectStockNewsByCode(stockCode, loadCodeToNameMap(), batchId);
        if (!result.isFullySuccessful()) {
            throw new IllegalStateException("股票 " + stockCode + " 新闻同步未完整成功，状态=" + result.status());
        }
        return result.savedCount();
    }

    /**
     * 采集指定股票的新闻和公告，并在 MongoDB 中记录本次同步水位。
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
        StockInfo stockInfo = syncStateService.markStarted(stockCode, batchId);

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
        syncStateService.complete(stockInfo, status, totalSaved, errorCode);
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
        int totalRefreshed = 0;
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

            // 批量预查候选 ID，完整记录直接跳过，截断记录允许重新采集覆盖。
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

            Map<String, StockNews> existingNews = externalIds.isEmpty() ? Map.of()
                    : newsRepository.findByStockCodeAndExternalIdIn(stockCode, externalIds).stream()
                            .filter(news -> news.getExternalId() != null)
                            .collect(Collectors.toMap(StockNews::getExternalId, news -> news, (left, right) -> left));
            List<StockNews> refreshBatch = new ArrayList<>();

            for (int i = 0; i < externalIds.size(); i++) {
                String externalId = externalIds.get(i);
                if (!seenIdsInRun.add(externalId)) {
                    totalSkipped++;
                    continue;
                }
                StockNews existing = existingNews.get(externalId);
                if (existing != null && hasSubstantiveContent(existing)) {
                    totalSkipped++;
                    log.debug("{} externalId={} 已存在，跳过", src.category, externalId);
                    continue;
                }

                StockNews news = newsDocumentFactory.create(externalId, titles.get(i), dateStrs.get(i),
                        stockCode, stockName, src.menuId(), src.category());
                if (news != null) {
                    newsCollectionPipelineService.process(news);
                    if (existing == null) {
                        batch.add(news);
                        if (batch.size() >= BATCH_INSERT_SIZE) {
                            totalSaved += batchInsert(batch, src.category);
                            batch.clear();
                        }
                    } else {
                        news.setId(existing.getId());
                        news.setCreateTime(existing.getCreateTime());
                        refreshBatch.add(news);
                    }
                }
                sleep(REQUEST_DELAY_MS);
            }

            if (!refreshBatch.isEmpty()) {
                totalRefreshed += batchRefresh(refreshBatch, src.category);
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

        log.info("股票 {} {} 采集完成，新增 {} 条，修复截断记录 {} 条，已存在跳过 {} 条（单股进度 {}/{}）",
                label, src.category, totalSaved, totalRefreshed, totalSkipped, sourceIndex, sourceTotal);
        return totalSaved + totalRefreshed;
    }

    private boolean hasSubstantiveContent(StockNews news) {
        String title = firstNonBlank(news.getNormalizedTitle(), news.getTitle(), news.getRawTitle());
        String content = firstNonBlank(news.getNormalizedContent(), news.getContent(), news.getRawContent());
        return contentQualityValidator.hasSubstantiveContent(title, content);
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return "";
    }

    private int batchRefresh(List<StockNews> batch, String category) {
        List<StockNews> refreshed = newsRepository.saveAll(batch);
        NewsEventFactSyncResult factResult = newsEventFactService.synchronizeBatch(refreshed);
        log.info("股票 {} {} 截断新闻修复完成: 原始文档={}，新增事件事实={}，重复文档={}",
                refreshed.get(0).getStockCode(), category, refreshed.size(), factResult.insertedEventCount(),
                factResult.duplicateDocumentCount());
        return refreshed.size();
    }

    /**
     * 批量写入原始新闻，并在原始文档成功后同步规范事件事实。
     *
     * @param batch 已完成采集治理的新闻文档
     * @param category 供应商原始渠道名称
     * @return 成功写入的原始新闻数量
     */
    private int batchInsert(List<StockNews> batch, String category) {
        if (batch.isEmpty()) {
            return 0;
        }
        StockNews first = batch.get(0);
        String label = first.getStockCode() + (first.getStockName() != null && !first.getStockName().isEmpty()
                ? " " + first.getStockName() : "");
        int savedCount;
        try {
            BulkOperations bulkOps = mongoTemplate.bulkOps(BulkOperations.BulkMode.UNORDERED, StockNews.class);
            for (StockNews news : batch) {
                bulkOps.insert(news);
            }
            bulkOps.execute();
            savedCount = batch.size();
        } catch (Exception exception) {
            log.warn("股票 {} {} 原始新闻批量写入失败，回退逐条保存: {}",
                    label, category, exception.getMessage());
            savedCount = 0;
            for (StockNews news : batch) {
                try {
                    newsRepository.save(news);
                    savedCount++;
                } catch (Exception itemException) {
                    log.warn("原始新闻保存失败 externalId={}: {}",
                            news.getExternalId(), itemException.getMessage());
                }
            }
            if (savedCount != batch.size()) {
                throw new IllegalStateException("MongoDB 原始新闻批量及逐条回退写入均未完整成功");
            }
        }

        NewsEventFactSyncResult factResult = newsEventFactService.synchronizeBatch(batch);
        log.info("股票 {} {} 写入完成: 原始文档={}，新增事件事实={}，重复文档={}",
                label, category, savedCount, factResult.insertedEventCount(),
                factResult.duplicateDocumentCount());
        return savedCount;
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
            log.info("股票 {} 原始新闻清理完成: 按时间删除 {} 条，按数量删除 {} 条，保留上限 {} 条/股；事件事实独立长期保留",
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
        String batchId = UUID.randomUUID().toString();
        log.info("开始批量采集股票新闻（{} 线程并行），最多 {} 只股票，batchId={}",
                PARALLEL_THREADS, maxStocks <= 0 ? "全部" : maxStocks, batchId);
        List<String> codes = stockInfoRepository.findAllCodes();
        if (codes == null || codes.isEmpty()) {
            log.warn("未找到任何股票代码，跳过新闻采集");
            return new CollectResult(batchId, 0, 0, 0, 0, 0, 0);
        }

        int limit = maxStocks > 0 ? Math.min(maxStocks, codes.size()) : codes.size();
        Map<String, String> codeToName = loadCodeToNameMap();

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
                    log.info("股票 {} 采集完成（状态={}，本股新增或修复 {} 条）",
                            resolveStockLabel(code, codeToName), result.status(), result.savedCount());
                } catch (Exception e) {
                    failedCount.incrementAndGet();
                    log.error("股票 {} 新闻采集失败: {}", resolveStockLabel(code, codeToName), e.getMessage(), e);
                } finally {
                    int processed = processedCount.incrementAndGet();
                    if (processed % 20 == 0 || processed == limit) {
                        log.info("新闻采集整体进度: {}/{} 只股票，累计新增或修复 {} 条，部分失败 {} 只，失败 {} 只",
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
        log.info("批量新闻采集完成: batchId={}，处理 {} 只，新增或修复 {} 条，有变更 {} 只，无变更 {} 只，部分失败 {} 只，失败 {} 只，耗时 {} 分钟",
                batchId, processedCount.get(), totalSaved.get(), withChangeCount.get(), noChangeCount.get(),
                partialCount.get(), failedCount.get(), costMs / 60_000);
        return new CollectResult(batchId, processedCount.get(), totalSaved.get(), withChangeCount.get(),
                noChangeCount.get(), partialCount.get(), failedCount.get());
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
     * 加载当前有效股票的代码名称映射，供单次采集流程复用。
     *
     * @return 股票代码到名称的只读快照
     */
    private Map<String, String> loadCodeToNameMap() {
        Map<String, String> codeToName = stockInfoRepository.findAll().stream()
                .filter(stock -> stock.getCode() != null && !DelistedStockDetector.isDelisted(stock))
                .collect(Collectors.toUnmodifiableMap(
                        StockInfo::getCode,
                        stock -> stock.getName() == null ? "" : stock.getName(),
                        (left, right) -> left));
        log.debug("股票代码名称映射已加载，共 {} 条", codeToName.size());
        return codeToName;
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
     * @param savedCount 新增或修复新闻数量
     * @param withChangeCount 有变更股票数量
     * @param noChangeCount 无变更股票数量
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
// AI_GENERATE_END---------------------
