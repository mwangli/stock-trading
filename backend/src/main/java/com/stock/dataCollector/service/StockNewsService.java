package com.stock.dataCollector.service;

import com.stock.dataCollector.domain.entity.StockNews;
import com.stock.dataCollector.persistence.NewsRepository;
import com.stock.dataCollector.persistence.StockInfoRepository;
import com.stock.dataCollector.support.SecuritiesClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.BulkOperations;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;

import jakarta.annotation.PreDestroy;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
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
    /** 每页条数，接口支持最大 100，只采集最新数据 */
    private static final int PAGE_SIZE = 100;
    /** 每只股票每个来源（新闻/公告）最多采集页数，历史新闻无意义 */
    private static final int MAX_PAGES = 1;
    /** 单条详情请求间隔，降低限流风险 */
    private static final long REQUEST_DELAY_MS = 150;
    /** 并行采集线程数 */
    private static final int PARALLEL_THREADS = 6;
    /** 批量写入阈值，与每页 100 条对齐，一次写入整页 */
    private static final int BATCH_INSERT_SIZE = 100;

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
     * 采集指定股票的新闻和公告
     *
     * @param stockCode 股票代码，如 600519
     * @return 本次采集新增的新闻数量
     */
    public int collectStockNewsByCode(String stockCode) {
        log.info("开始采集股票 {} 的新闻与公告", stockCode);
        int totalSaved = 0;
        for (NewsSource src : NEWS_SOURCES) {
            log.info("股票 {} 开始采集{} (menu_id={})", stockCode, src.category, src.menuId);
            int saved = collectBySource(stockCode, src);
            totalSaved += saved;
        }
        return totalSaved;
    }

    /**
     * 按采集来源（新闻/公告）采集，批量写入
     */
    private int collectBySource(String stockCode, NewsSource src) {
        List<StockNews> batch = new ArrayList<>(BATCH_INSERT_SIZE);
        int totalSaved = 0;
        int totalSkipped = 0;
        int page = 1;
        boolean hasMore = true;

        while (hasMore && page <= MAX_PAGES) {
            Map<String, Object> listResp = securitiesClient.requestNewsList(stockCode, page, PAGE_SIZE, src.menuId);
            if (listResp.isEmpty()) {
                log.info("股票 {} {} 第 {} 页列表返回为空，跳过", stockCode, src.category, page);
                break;
            }

            Object grid0Obj = listResp.get("GRID0");
            if (!(grid0Obj instanceof List<?> grid0)) {
                break;
            }

            if (grid0.isEmpty()) {
                log.info("股票 {} {} 第 {} 页无数据，跳过", stockCode, src.category, page);
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
            log.info("股票 {} {} 第 {} 页: 采集到 {} 条标题，示例: {}", stockCode, src.category, page, titles.size(), titlePreview);

            Set<String> existingIds = externalIds.isEmpty() ? Set.of()
                    : newsRepository.findExternalIdsByStockCodeAndExternalIdIn(stockCode, externalIds).stream()
                            .map(StockNews::getExternalId)
                            .filter(Objects::nonNull)
                            .collect(Collectors.toSet());
            int skipCount = (int) externalIds.stream().filter(existingIds::contains).count();
            totalSkipped += skipCount;

            for (int i = 0; i < externalIds.size(); i++) {
                String externalId = externalIds.get(i);
                if (existingIds.contains(externalId)) {
                    log.debug("{} externalId={} 已存在，跳过", src.category, externalId);
                    continue;
                }

                StockNews news = fetchDetail(externalId, titles.get(i), dateStrs.get(i), stockCode, src);
                if (news != null) {
                    batch.add(news);
                    if (batch.size() >= BATCH_INSERT_SIZE) {
                        batchInsert(batch, src.category);
                        totalSaved += batch.size();
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
            batchInsert(batch, src.category);
            totalSaved += batch.size();
        }

        log.info("股票 {} {} 采集完成，新增 {} 条，已存在跳过 {} 条", stockCode, src.category, totalSaved, totalSkipped);
        return totalSaved;
    }

    /**
     * 批量插入 MongoDB
     */
    private void batchInsert(List<StockNews> batch, String category) {
        if (batch.isEmpty()) {
            return;
        }
        try {
            BulkOperations bulkOps = mongoTemplate.bulkOps(BulkOperations.BulkMode.UNORDERED, StockNews.class);
            for (StockNews news : batch) {
                bulkOps.insert(news);
            }
            bulkOps.execute();
            log.info("批量写入{} {} 条", category, batch.size());
        } catch (Exception e) {
            log.warn("批量写入失败，回退逐条保存: {}", e.getMessage());
            for (StockNews news : batch) {
                try {
                    newsRepository.save(news);
                } catch (Exception ex) {
                    log.warn("保存失败 externalId={}: {}", news.getExternalId(), ex.getMessage());
                }
            }
        }
    }

    /**
     * 批量采集所有股票的新闻（多线程并行）
     *
     * @param maxStocks 最多采集的股票数量，0 表示不限制
     * @return 采集结果统计
     */
    public CollectResult collectAllStockNews(int maxStocks) {
        log.info("开始批量采集股票新闻（{} 线程并行），最多 {} 只股票", PARALLEL_THREADS, maxStocks <= 0 ? "全部" : maxStocks);
        List<String> codes = stockInfoRepository.findAllCodes();
        if (codes == null || codes.isEmpty()) {
            log.warn("未找到任何股票代码，跳过新闻采集");
            return new CollectResult(0, 0, 0);
        }

        int limit = maxStocks > 0 ? Math.min(maxStocks, codes.size()) : codes.size();
        AtomicInteger totalSaved = new AtomicInteger(0);
        AtomicInteger processedCount = new AtomicInteger(0);
        AtomicInteger failedCount = new AtomicInteger(0);
        CountDownLatch latch = new CountDownLatch(limit);

        long startMs = System.currentTimeMillis();

        for (int i = 0; i < limit; i++) {
            String code = codes.get(i);
            executor.submit(() -> {
                try {
                    int saved = collectStockNewsByCode(code);
                    totalSaved.addAndGet(saved);
                    processedCount.incrementAndGet();
                    int p = processedCount.get();
                    if (p % 100 == 0) {
                        log.info("新闻采集进度: {}/{} 只股票，累计新增 {} 条", p, limit, totalSaved.get());
                    }
                } catch (Exception e) {
                    failedCount.incrementAndGet();
                    log.error("股票 {} 新闻采集失败: {}", code, e.getMessage(), e);
                } finally {
                    latch.countDown();
                }
            });
        }

        try {
            latch.await(24, TimeUnit.HOURS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("新闻采集被中断");
        }

        long costMs = System.currentTimeMillis() - startMs;
        log.info("批量新闻采集完成: 处理 {} 只股票，新增 {} 条，失败 {} 只，耗时 {} 分钟",
                processedCount.get(), totalSaved.get(), failedCount.get(), costMs / 60_000);
        return new CollectResult(processedCount.get(), totalSaved.get(), failedCount.get());
    }

    /**
     * 采集股票新闻（兼容原有方法签名，默认采集全部）
     */
    public void collectStockNews() {
        collectAllStockNews(0);
    }

    /**
     * 采集股票新闻（兼容原有方法签名）
     */
    public void collectAllStockNews() {
        collectAllStockNews(0);
    }

    /**
     * 拉取新闻/公告详情（不写入，由调用方批量写入）
     */
    private StockNews fetchDetail(String externalId, String listTitle, String dateStr,
                                  String stockCode, NewsSource src) {
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

    private void sleep(long ms) {
        try {
            TimeUnit.MILLISECONDS.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("新闻采集延迟被中断");
        }
    }

    /**
     * 新闻采集结果
     */
    public record CollectResult(int processedCount, int savedCount, int failedCount) {}

    /** 采集来源（新闻/公告） */
    private record NewsSource(String menuId, String category) {}
}
