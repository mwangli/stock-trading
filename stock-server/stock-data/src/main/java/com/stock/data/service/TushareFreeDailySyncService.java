// AI_GENERATE_START--- --
package com.stock.data.service;

import com.mongodb.MongoSocketException;
import com.stock.data.client.TushareDailyClient;
import com.stock.data.client.TushareDailyClient.TushareDailyBar;
import com.stock.data.config.TushareProperties;
import com.stock.data.dto.MarketDataImportResultDto;
import com.stock.data.dto.ModelStockDailyBarImportItemDto;
import com.stock.data.dto.ModelStockDailyBarImportRequestDto;
import com.stock.data.dto.TushareFreeDailyBackfillResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Tushare 免费日线到 V6 模型专用行情的同步服务。
 * 历史回填按日期获取全市场数据，主动限速并以固定来源版本幂等写入 MongoDB。
 *
 * @author mwangli
 * @since 2026-10-26
 */
@Slf4j
@Service
public class TushareFreeDailySyncService {

    /** V6 免费日线来源标识。 */
    public static final String SOURCE = "TUSHARE_FREE_DAILY";
    /** V6 免费日线不可变契约版本。 */
    public static final String SOURCE_VERSION = "V6";
    private static final int STORAGE_WRITE_MAX_ATTEMPTS = 3;
    private static final long STORAGE_RETRY_DELAY_SECONDS = 2L;

    private final TushareDailyClient client;
    private final TushareProperties properties;
    private final MarketDataFoundationService foundationService;
    private final MarketDataQueryService marketDataQueryService;
    private long lastRequestNanos;

    /**
     * 创建 Tushare 免费日线同步服务。
     *
     * @param client Tushare 日线客户端
     * @param properties Tushare 配置
     * @param foundationService 版本化市场数据写入服务
     * @param marketDataQueryService 版本化市场数据查询服务
     */
    public TushareFreeDailySyncService(
            TushareDailyClient client,
            TushareProperties properties,
            MarketDataFoundationService foundationService,
            MarketDataQueryService marketDataQueryService) {
        this.client = client;
        this.properties = properties;
        this.foundationService = foundationService;
        this.marketDataQueryService = marketDataQueryService;
    }

    /**
     * 判断当前进程是否具备免费日线回填条件。
     *
     * @return Token 和接口配置完整时返回 true
     */
    public boolean isConfigured() {
        return properties.isConfigured();
    }

    /**
     * 回填指定股票池所需的历史免费日线。
     *
     * @param stockCodes 训练股票代码
     * @param tradingDays 期望覆盖的最大交易日数量
     * @return 回填统计
     * @throws IllegalStateException 配置、接口或线程状态异常时抛出
     */
    public synchronized TushareFreeDailyBackfillResult backfill(
            Collection<String> stockCodes, int tradingDays) {
        Set<String> requestedCodes = normalizedCodes(stockCodes);
        if (requestedCodes.isEmpty()) {
            throw new IllegalStateException("Tushare V6 回填股票范围为空");
        }
        LocalDate endDate = LocalDate.now().minusDays(1);
        long calendarDays = Math.max(180L, (long) Math.ceil(Math.max(1, tradingDays) * 1.6D) + 180L);
        LocalDate earliestRequiredDate = endDate.minusDays(calendarDays);
        LocalDate latestPersistedDate = marketDataQueryService.findLatestModelStockBarDate(
                SOURCE, SOURCE_VERSION);
        LocalDate startDate = latestPersistedDate != null
                && !latestPersistedDate.isBefore(earliestRequiredDate)
                ? latestPersistedDate : earliestRequiredDate;
        if (latestPersistedDate != null && startDate.equals(latestPersistedDate)) {
            log.info("Tushare V6 历史日线从已落库边界日续跑: latestPersistedDate={}, endDate={}",
                    latestPersistedDate, endDate);
        }
        int requestCount = 0;
        long returnedRows = 0L;
        long upsertedRows = 0L;
        Set<String> coveredCodes = new LinkedHashSet<>();
        for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
            if (isWeekend(date)) {
                continue;
            }
            List<TushareDailyBar> bars = fetchRateLimited(date);
            requestCount++;
            List<ModelStockDailyBarImportItemDto> items = bars.stream()
                    .filter(bar -> requestedCodes.contains(stockCode(bar.tsCode())))
                    .map(this::toImportItem)
                    .toList();
            if (!items.isEmpty()) {
                MarketDataImportResultDto imported = importDailyBarsWithRetry(date, items);
                returnedRows += items.size();
                upsertedRows += imported.getUpsertedCount();
                items.forEach(item -> coveredCodes.add(item.getStockCode()));
            }
            if (requestCount % 20 == 0) {
                log.info("Tushare V6 历史日线回填进度: date={}, requests={}, rows={}, coveredStocks={}",
                        date, requestCount, returnedRows, coveredCodes.size());
            }
        }
        log.info("Tushare V6 历史日线回填完成: range={}~{}, requests={}, rows={}, coveredStocks={}",
                startDate, endDate, requestCount, returnedRows, coveredCodes.size());
        return new TushareFreeDailyBackfillResult(startDate, endDate, requestCount,
                returnedRows, upsertedRows, coveredCodes.size());
    }

    /**
     * 同步指定日期的 V6 全市场免费日线。
     *
     * @param tradeDate 目标交易日
     * @param stockCodes 当前允许写入的股票代码
     * @return 当日回填统计
     */
    public synchronized TushareFreeDailyBackfillResult syncTradeDate(
            LocalDate tradeDate, Collection<String> stockCodes) {
        Set<String> requestedCodes = normalizedCodes(stockCodes);
        List<ModelStockDailyBarImportItemDto> items = fetchRateLimited(tradeDate).stream()
                .filter(bar -> requestedCodes.contains(stockCode(bar.tsCode())))
                .map(this::toImportItem)
                .toList();
        int upserted = 0;
        Set<String> coveredCodes = new LinkedHashSet<>();
        if (!items.isEmpty()) {
            MarketDataImportResultDto imported = importDailyBarsWithRetry(tradeDate, items);
            upserted = imported.getUpsertedCount();
            items.forEach(item -> coveredCodes.add(item.getStockCode()));
        }
        return new TushareFreeDailyBackfillResult(tradeDate, tradeDate, 1,
                items.size(), upserted, coveredCodes.size());
    }

    private MarketDataImportResultDto importDailyBarsWithRetry(
            LocalDate tradeDate, List<ModelStockDailyBarImportItemDto> items) {
        ModelStockDailyBarImportRequestDto request = ModelStockDailyBarImportRequestDto.builder()
                .source(SOURCE)
                .sourceVersion(SOURCE_VERSION)
                .volumeUnit("LOT")
                .amountUnit("CNY_THOUSAND")
                .items(items)
                .build();
        for (int attempt = 1; attempt <= STORAGE_WRITE_MAX_ATTEMPTS; attempt++) {
            try {
                return foundationService.importModelStockDailyBars(request);
            } catch (DataAccessResourceFailureException | MongoSocketException exception) {
                if (attempt == STORAGE_WRITE_MAX_ATTEMPTS) {
                    throw exception;
                }
                log.warn("MongoDB 瞬时连接异常，稍后重试 Tushare 单日写入: tradeDate={}, attempt={}/{}, reason={}",
                        tradeDate, attempt, STORAGE_WRITE_MAX_ATTEMPTS, exception.getMessage());
                try {
                    TimeUnit.SECONDS.sleep(STORAGE_RETRY_DELAY_SECONDS * attempt);
                } catch (InterruptedException interruptedException) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Tushare V6 日线写入重试被中断", interruptedException);
                }
            }
        }
        throw new IllegalStateException("Tushare V6 日线写入重试状态异常");
    }

    private List<TushareDailyBar> fetchRateLimited(LocalDate tradeDate) {
        waitForRateLimit();
        List<TushareDailyBar> result = client.fetchDaily(tradeDate);
        lastRequestNanos = System.nanoTime();
        return result;
    }

    private void waitForRateLimit() {
        int requestsPerMinute = properties.getRequestsPerMinute();
        if (requestsPerMinute <= 0 || requestsPerMinute > 120) {
            throw new IllegalStateException("Tushare requestsPerMinute 必须在 1 到 120 之间");
        }
        if (lastRequestNanos == 0L) {
            return;
        }
        long intervalNanos = TimeUnit.MINUTES.toNanos(1) / requestsPerMinute;
        long remaining = intervalNanos - (System.nanoTime() - lastRequestNanos);
        if (remaining <= 0L) {
            return;
        }
        try {
            TimeUnit.NANOSECONDS.sleep(remaining);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Tushare V6 日线回填被中断", exception);
        }
    }

    private ModelStockDailyBarImportItemDto toImportItem(TushareDailyBar bar) {
        return ModelStockDailyBarImportItemDto.builder()
                .stockCode(stockCode(bar.tsCode()))
                .tradeDate(bar.tradeDate())
                .openPrice(bar.openPrice())
                .highPrice(bar.highPrice())
                .lowPrice(bar.lowPrice())
                .closePrice(bar.closePrice())
                .preClosePrice(bar.preClosePrice())
                .volume(bar.volume())
                .amount(bar.amount())
                .turnoverRate(null)
                .build();
    }

    private Set<String> normalizedCodes(Collection<String> stockCodes) {
        if (stockCodes == null) {
            return Set.of();
        }
        return stockCodes.stream()
                .filter(code -> code != null && !code.isBlank())
                .map(String::trim)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    }

    private String stockCode(String tsCode) {
        int separator = tsCode.indexOf('.');
        return separator > 0 ? tsCode.substring(0, separator) : tsCode;
    }

    private boolean isWeekend(LocalDate date) {
        return date.getDayOfWeek() == DayOfWeek.SATURDAY
                || date.getDayOfWeek() == DayOfWeek.SUNDAY;
    }
}
// AI_GENERATE_END--- --