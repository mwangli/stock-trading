// AI_GENERATE_START------------------
package com.stock.data.service;

import com.stock.data.dto.HistoricalStockUniverseDto;
import com.stock.data.dto.MarketNewsDto;
import com.stock.data.dto.MarketNewsEventDto;
import com.stock.data.dto.ModelReferenceDailyBarDto;
import com.stock.data.dto.ModelStockDailyBarDto;
import com.stock.data.dto.ModelTrainingDataAuditDto;
import com.stock.data.dto.StockAdjustmentFactorDto;
import com.stock.data.dto.StockInfoItemDto;
import com.stock.data.dto.StockPriceItemDto;
import com.stock.data.dto.StockTradabilityDto;
import com.stock.data.entity.HistoricalStockUniverseEntity;
import com.stock.data.entity.ModelReferenceDailyBar;
import com.stock.data.entity.NewsCollectionBackfillState;
import com.stock.data.entity.ReferenceSeriesType;
import com.stock.data.entity.StockAdjustmentFactor;
import com.stock.data.entity.StockDailyTradabilityFact;
import com.stock.data.entity.StockInfo;
import com.stock.data.entity.StockNews;
import com.stock.data.entity.StockNewsEventFact;
import com.stock.data.entity.StockPrice;
import com.stock.data.enums.NewsBackfillStatus;
import com.stock.data.enums.NewsDocumentRole;
import com.stock.data.repository.HistoricalStockUniverseRepository;
import com.stock.data.repository.ModelReferenceDailyBarRepository;
import com.stock.data.repository.NewsEventFactRepository;
import com.stock.data.repository.NewsRepository;
import com.stock.data.repository.PriceRepository;
import com.stock.data.repository.StockAdjustmentFactorRepository;
import com.stock.data.repository.StockDailyTradabilityFactRepository;
import com.stock.data.repository.StockInfoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 向模型、决策、执行和平台包提供只读市场事实。
 *
 * @author mwangli
 * @since 2026-10-29
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class MarketDataQueryService {
    private static final int MODEL_STOCK_BAR_BATCH_SIZE = 100;
    private static final int NEWS_EVENT_QUERY_BATCH_SIZE = 200;
    private static final int NEWS_EVENT_QUERY_MAX_ATTEMPTS = 3;
    private static final long NEWS_EVENT_QUERY_RETRY_DELAY_MS = 1_000L;
    private final PriceRepository priceRepository;
    private final StockInfoRepository stockInfoRepository;
    private final NewsRepository newsRepository;
    private final NewsEventFactRepository newsEventFactRepository;
    private final ModelReferenceDailyBarRepository modelReferenceDailyBarRepository;
    private final StockAdjustmentFactorRepository adjustmentFactorRepository;
    private final HistoricalStockUniverseRepository historicalUniverseRepository;
    private final StockDailyTradabilityFactRepository tradabilityFactRepository;
    private final MongoTemplate mongoTemplate;

    /**
     * 查询股票最近日线窗口。
     *
     * @param stockCode 股票代码
     * @param endDate 截止日期，包含当天；为空时查询最新窗口
     * @param limit 最大返回条数
     * @return 日期倒序的日线 DTO
     */
    public List<StockPriceItemDto> findRecentPrices(String stockCode, LocalDate endDate, int limit) {
        PageRequest page = PageRequest.of(0, Math.max(1, limit));
        List<StockPrice> prices = endDate == null
                ? priceRepository.findByCodeOrderByDateDesc(stockCode, page)
                : priceRepository.findByCodeAndDateLessThanEqualOrderByDateDesc(stockCode, endDate, page);
        return prices.stream().map(this::toPriceDto).toList();
    }

    /**
     * 查询股票指定日期区间日线。
     *
     * @param stockCode 股票代码
     * @param startDate 开始日期
     * @param endDate 结束日期
     * @return 日期升序的日线 DTO
     */
    public List<StockPriceItemDto> findPrices(String stockCode, LocalDate startDate, LocalDate endDate) {
        return priceRepository.findByCodeAndDateBetweenOrderByDateAsc(stockCode, startDate, endDate)
                .stream().map(this::toPriceDto).toList();
    }

    /**
     * 查询指定股票和交易日的日线。
     *
     * @param stockCode 股票代码
     * @param tradeDate 交易日期
     * @return 日线 DTO
     */
    public Optional<StockPriceItemDto> findPrice(String stockCode, LocalDate tradeDate) {
        return priceRepository.findByCodeAndDate(stockCode, tradeDate).map(this::toPriceDto);
    }

    /**
     * 批量查询指定交易日的股票日线。
     *
     * @param stockCodes 股票代码
     * @param tradeDate 交易日期
     * @return 日线 DTO 列表
     */
    public List<StockPriceItemDto> findPrices(List<String> stockCodes, LocalDate tradeDate) {
        return priceRepository.findByCodeInAndDate(stockCodes, tradeDate).stream()
                .map(this::toPriceDto).toList();
    }

    /**
     * 查询全市场最新日线日期。
     *
     * @return 最新行情日期，不存在时返回 null
     */
    public LocalDate findLatestMarketDate() {
        return priceRepository.findTopByOrderByDateDesc().map(StockPrice::getDate).orElse(null);
    }

    /**
     * 统计指定股票集合在交易日的行情覆盖数量。
     *
     * @param stockCodes 股票代码
     * @param tradeDate 交易日期
     * @return 已有行情数量
     */
    public long countPriceCoverage(List<String> stockCodes, LocalDate tradeDate) {
        return priceRepository.countByCodeInAndDate(stockCodes, tradeDate);
    }

    /**
     * 查询当前可交易股票代码。
     *
     * @return 可交易股票代码
     */
    public List<String> findTradableStockCodes() {
        return stockInfoRepository.findAllCodes();
    }

    /**
     * 统计当前可交易股票数量。
     *
     * @return 可交易股票数量
     */
    public long countTradableStocks() {
        return stockInfoRepository.countTradable();
    }

    /**
     * 统计当前股票基础数据数量。
     *
     * @return 股票基础数据数量
     */
    public long countStocks() {
        return stockInfoRepository.count();
    }

    /**
     * 批量查询股票基础信息。
     *
     * @param stockCodes 股票代码
     * @return 股票基础信息 DTO
     */
    public List<StockInfoItemDto> findStocks(Collection<String> stockCodes) {
        return stockInfoRepository.findByCodeIn(List.copyOf(stockCodes)).stream()
                .map(this::toStockInfoDto).toList();
    }

    /**
     * 按市值倒序查询股票代码。
     *
     * @param limit 最大返回数量
     * @return 股票代码
     */
    public List<String> findTopStockCodes(int limit) {
        PageRequest page = PageRequest.of(0, Math.max(1, limit),
                Sort.by(Sort.Order.desc("totalMarketValue"), Sort.Order.asc("code")));
        return stockInfoRepository.findAll(page).getContent().stream().map(StockInfo::getCode).toList();
    }

    /**
     * 查询窗口内指定角色的新闻事件事实。
     *
     * @param stockCodes 股票代码
     * @param startTime 窗口开始时间
     * @param endTime 窗口结束时间
     * @param roles 允许的文档角色
     * @return 新闻事件事实 DTO
     */
    public List<MarketNewsEventDto> findNewsEvents(Collection<String> stockCodes,
                                                    LocalDateTime startTime,
                                                    LocalDateTime endTime,
                                                    Collection<NewsDocumentRole> roles) {
        List<String> normalizedStockCodes = stockCodes == null ? List.of() : stockCodes.stream()
                .filter(code -> code != null && !code.isBlank())
                .map(String::trim)
                .distinct()
                .toList();
        if (normalizedStockCodes.isEmpty()) {
            return List.of();
        }
        List<StockNewsEventFact> facts = new ArrayList<>();
        for (int start = 0; start < normalizedStockCodes.size(); start += NEWS_EVENT_QUERY_BATCH_SIZE) {
            int end = Math.min(start + NEWS_EVENT_QUERY_BATCH_SIZE, normalizedStockCodes.size());
            facts.addAll(findNewsEventBatchWithRetry(
                    normalizedStockCodes.subList(start, end), startTime, endTime, roles));
        }
        facts.sort(Comparator.comparing(
                StockNewsEventFact::getPublishTime,
                Comparator.nullsLast(Comparator.reverseOrder())));
        return facts.stream().map(this::toNewsEventDto).toList();
    }

    private List<StockNewsEventFact> findNewsEventBatchWithRetry(
            Collection<String> stockCodes,
            LocalDateTime startTime,
            LocalDateTime endTime,
            Collection<NewsDocumentRole> roles) {
        for (int attempt = 1; attempt <= NEWS_EVENT_QUERY_MAX_ATTEMPTS; attempt++) {
            try {
                return newsEventFactRepository
                        .findByStockCodeInAndPublishTimeBetweenAndDocumentRoleInOrderByPublishTimeDesc(
                                stockCodes, startTime, endTime, roles);
            } catch (DataAccessResourceFailureException exception) {
                if (attempt >= NEWS_EVENT_QUERY_MAX_ATTEMPTS) {
                    throw exception;
                }
                log.warn("批量新闻事件查询连接异常，准备重试: stocks={}, attempt={}/{}, reason={}",
                        stockCodes.size(), attempt, NEWS_EVENT_QUERY_MAX_ATTEMPTS,
                        exception.getMostSpecificCause().getMessage());
                sleepBeforeNewsEventRetry();
            }
        }
        throw new IllegalStateException("批量新闻事件查询重试流程异常结束");
    }

    private void sleepBeforeNewsEventRetry() {
        try {
            Thread.sleep(NEWS_EVENT_QUERY_RETRY_DELAY_MS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("批量新闻事件查询重试被中断", exception);
        }
    }

    /**
     * 查询指定角色的全部新闻事件事实。
     *
     * @param roles 允许的文档角色
     * @return 新闻事件事实 DTO
     */
    public List<MarketNewsEventDto> findNewsEvents(Collection<NewsDocumentRole> roles) {
        return newsEventFactRepository.findByDocumentRoleIn(roles).stream()
                .map(this::toNewsEventDto).toList();
    }

    /**
     * 按发布时间顺序分页查询指定角色新闻事件。
     *
     * @param role 文档角色
     * @param startTime 开始时间
     * @param endTime 结束时间
     * @param page 页码，从 0 开始
     * @param size 每页数量
     * @return 新闻事件事实 DTO
     */
    public List<MarketNewsEventDto> findNewsEvents(NewsDocumentRole role,
                                                    LocalDateTime startTime,
                                                    LocalDateTime endTime,
                                                    int page,
                                                    int size) {
        return newsEventFactRepository.findByDocumentRoleAndPublishTimeBetweenOrderByPublishTimeAscIdAsc(
                        role, startTime, endTime, PageRequest.of(page, size))
                .stream().map(this::toNewsEventDto).toList();
    }

    /**
     * 查询决策时点前最近五条新闻。
     *
     * @param stockCode 股票代码
     * @param startTime 窗口开始时间
     * @param endTime 信息截止时间
     * @return 新闻证据摘要
     */
    public List<MarketNewsDto> findRecentNews(String stockCode,
                                              LocalDateTime startTime,
                                              LocalDateTime endTime) {
        return newsRepository.findTop5ByStockCodeAndPublishTimeBetweenOrderByPublishTimeDesc(
                        stockCode, startTime, endTime)
                .stream().map(this::toNewsDto).toList();
    }

    /**
     * 查询版本化模型股票日线窗口。
     *
     * @param stockCode 股票代码
     * @param endDate 截止日期，包含当天
     * @param source 数据来源
     * @param sourceVersion 数据版本
     * @param limit 最大返回数量
     * @return 模型股票日线 DTO
     */
    public List<ModelStockDailyBarDto> findModelStockBars(String stockCode,
                                                          LocalDate endDate,
                                                          String source,
                                                          String sourceVersion,
                                                          int limit) {
        PageRequest page = PageRequest.of(0, Math.max(1, limit));
        List<StockPrice> bars = endDate == null
                ? priceRepository
                .findByCodeAndSourceAndSourceVersionOrderByDateDesc(
                        stockCode, source, sourceVersion, page)
                : priceRepository
                .findByCodeAndDateLessThanEqualAndSourceAndSourceVersionOrderByDateDesc(
                        stockCode, endDate, source, sourceVersion, page);
        return bars.stream().map(this::toModelStockBarDto).toList();
    }

    /**
     * 批量查询版本化模型股票日线窗口，消除训练快照逐股远程查询。
     *
     * @param stockCodes 股票代码集合
     * @param endDate 截止日期，包含当天
     * @param source 数据来源
     * @param sourceVersion 数据版本
     * @param limitPerStock 每只股票最大返回数量
     * @return 股票代码到按交易日倒序排列的模型日线映射
     */
    public Map<String, List<ModelStockDailyBarDto>> findModelStockBarsByStockCodes(
            Collection<String> stockCodes,
            LocalDate endDate,
            String source,
            String sourceVersion,
            int limitPerStock) {
        if (stockCodes == null || stockCodes.isEmpty()) {
            return Map.of();
        }
        int safeLimit = Math.max(1, limitPerStock);
        List<String> codes = stockCodes.stream()
                .filter(code -> code != null && !code.isBlank())
                .map(String::trim)
                .distinct()
                .toList();
        Map<String, List<ModelStockDailyBarDto>> grouped = new LinkedHashMap<>();
        for (int start = 0; start < codes.size(); start += MODEL_STOCK_BAR_BATCH_SIZE) {
            int end = Math.min(start + MODEL_STOCK_BAR_BATCH_SIZE, codes.size());
            List<String> batchCodes = codes.subList(start, end);
            List<StockPrice> bars = endDate == null
                    ? priceRepository
                    .findByCodeInAndSourceAndSourceVersionOrderByCodeAscDateDesc(
                            batchCodes, source, sourceVersion)
                    : priceRepository
                    .findByCodeInAndDateLessThanEqualAndSourceAndSourceVersionOrderByCodeAscDateDesc(
                            batchCodes, endDate, source, sourceVersion);
            for (StockPrice bar : bars) {
                List<ModelStockDailyBarDto> stockBars = grouped.computeIfAbsent(
                        bar.getCode(), ignored -> new ArrayList<>());
                if (stockBars.size() < safeLimit) {
                    stockBars.add(toModelStockBarDto(bar));
                }
            }
        }
        return grouped;
    }

    /**
     * 查询固定来源版本包含的去重交易日。
     *
     * @param source 数据来源
     * @param sourceVersion 数据版本
     * @return 按日期升序排列的交易日
     */
    public List<LocalDate> findModelStockBarDates(String source, String sourceVersion) {
        Query query = Query.query(Criteria.where("source").is(source)
                .and("sourceVersion").is(sourceVersion));
        return mongoTemplate.findDistinct(
                        query, "date", StockPrice.class, LocalDate.class).stream()
                .filter(java.util.Objects::nonNull)
                .sorted()
                .toList();
    }

    /**
     * 查询点时窗口内指定角色涉及的去重股票代码。
     *
     * @param startTime 窗口开始时间，包含
     * @param endTime 信息截止时间，包含
     * @param roles 允许进入新闻特征的事件角色
     * @return 股票代码集合
     */
    public Set<String> findNewsEventStockCodes(
            LocalDateTime startTime,
            LocalDateTime endTime,
            Collection<NewsDocumentRole> roles) {
        Query query = Query.query(Criteria.where("publishTime").gte(startTime).lte(endTime)
                .and("documentRole").in(roles));
        return mongoTemplate.findDistinct(
                        query, "stockCode", StockNewsEventFact.class, String.class).stream()
                .filter(code -> code != null && !code.isBlank())
                .map(String::trim)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    }

    /**
     * 统计点时窗口内指定角色的去重事件事实数量。
     *
     * @param startTime 窗口开始时间，包含
     * @param endTime 信息截止时间，包含
     * @param roles 允许进入新闻特征的事件角色
     * @return 事件事实数量
     */
    public long countNewsEvents(
            LocalDateTime startTime,
            LocalDateTime endTime,
            Collection<NewsDocumentRole> roles) {
        Query query = Query.query(Criteria.where("publishTime").gte(startTime).lte(endTime)
                .and("documentRole").in(roles));
        return mongoTemplate.count(query, StockNewsEventFact.class);
    }

    /**
     * 查询点时窗口内指定角色事件的最晚发布时间。
     *
     * @param startTime 窗口开始时间，包含
     * @param endTime 信息截止时间，包含
     * @param roles 允许进入新闻特征的事件角色
     * @return 最晚发布时间；窗口内无事件时返回 null
     */
    public LocalDateTime findLatestNewsEventTime(
            LocalDateTime startTime,
            LocalDateTime endTime,
            Collection<NewsDocumentRole> roles) {
        Query query = Query.query(Criteria.where("publishTime").gte(startTime).lte(endTime)
                        .and("documentRole").in(roles))
                .with(Sort.by(Sort.Direction.DESC, "publishTime"))
                .limit(1);
        query.fields().include("publishTime");
        StockNewsEventFact latest = mongoTemplate.findOne(query, StockNewsEventFact.class);
        return latest == null ? null : latest.getPublishTime();
    }

    /**
     * 判断指定新闻治理版本的历史清洗和事件事实回填是否完整结束。
     *
     * @param pipelineVersion 新闻治理管线版本
     * @return 历史治理状态为 COMPLETED 时返回 true
     */
    public boolean isNewsCollectionBackfillComplete(String pipelineVersion) {
        NewsCollectionBackfillState state = mongoTemplate.findById(
                pipelineVersion, NewsCollectionBackfillState.class);
        return state != null && state.getStatus() == NewsBackfillStatus.COMPLETED;
    }

    /**
     * 查询固定来源版本的最新模型日线日期。
     *
     * @param source 数据来源
     * @param sourceVersion 数据版本
     * @return 最新数据日期，不存在时返回 null
     */
    public LocalDate findLatestModelStockBarDate(String source, String sourceVersion) {
        return priceRepository.findFirstBySourceAndSourceVersionOrderByDateDesc(source, sourceVersion)
                .map(StockPrice::getDate).orElse(null);
    }

    /**
     * 审计指定来源版本是否具备模型训练要求的基础集合。
     *
     * @param stockCodes 本次训练要求的股票代码
     * @param source 数据来源
     * @param sourceVersion 数据来源版本
     * @param benchmarkCode 市场基准序列代码
     * @return 模型训练基础数据审计结果
     */
    public ModelTrainingDataAuditDto auditModelTrainingData(
            Collection<String> stockCodes,
            String source,
            String sourceVersion,
            String benchmarkCode) {
        Set<String> requestedCodes = normalizedValues(stockCodes);
        Query stockSourceScope = Query.query(Criteria.where("source").is(source)
                .and("sourceVersion").is(sourceVersion)
                .and("code").in(requestedCodes));
        Set<String> modelBarCodes = distinctValues(
                stockSourceScope, "code", StockPrice.class);
        Query relatedFactScope = Query.query(Criteria.where("source").is(source)
                .and("sourceVersion").is(sourceVersion)
                .and("stockCode").in(requestedCodes));
        Set<String> factorCodes = distinctValues(
                relatedFactScope, "stockCode", StockAdjustmentFactor.class);
        Set<String> universeCodes = distinctValues(
                relatedFactScope, "stockCode", HistoricalStockUniverseEntity.class);
        Set<String> tradabilityCodes = distinctValues(
                relatedFactScope, "stockCode", StockDailyTradabilityFact.class);
        Set<String> requiredIndustryCodes = distinctValues(
                Query.query(Criteria.where("source").is(source)
                        .and("sourceVersion").is(sourceVersion)
                        .and("stockCode").in(requestedCodes)
                        .and("industryCode").nin(java.util.Arrays.asList(null, ""))),
                "industryCode", HistoricalStockUniverseEntity.class);
        Set<String> availableIndustryCodes = distinctValues(
                Query.query(Criteria.where("source").is(source)
                        .and("sourceVersion").is(sourceVersion)
                        .and("seriesType").is(ReferenceSeriesType.INDUSTRY)),
                "seriesCode", ModelReferenceDailyBar.class);
        boolean benchmarkAvailable = mongoTemplate.exists(Query.query(Criteria.where("source").is(source)
                .and("sourceVersion").is(sourceVersion)
                .and("seriesType").is(ReferenceSeriesType.MARKET)
                .and("seriesCode").is(benchmarkCode)), ModelReferenceDailyBar.class);
        return ModelTrainingDataAuditDto.builder()
                .source(source)
                .sourceVersion(sourceVersion)
                .requestedStockCount(requestedCodes.size())
                .modelBarStockCount(intersectionSize(requestedCodes, modelBarCodes))
                .adjustmentFactorStockCount(intersectionSize(requestedCodes, factorCodes))
                .historicalUniverseStockCount(intersectionSize(requestedCodes, universeCodes))
                .tradabilityStockCount(intersectionSize(requestedCodes, tradabilityCodes))
                .requiredIndustrySeriesCount(requiredIndustryCodes.size())
                .availableIndustrySeriesCount(intersectionSize(requiredIndustryCodes, availableIndustryCodes))
                .benchmarkSeriesAvailable(benchmarkAvailable)
                .latestModelBarDate(findLatestModelStockBarDate(source, sourceVersion))
                .missingModelBarCodes(missingValues(requestedCodes, modelBarCodes))
                .missingAdjustmentFactorCodes(missingValues(requestedCodes, factorCodes))
                .missingHistoricalUniverseCodes(missingValues(requestedCodes, universeCodes))
                .missingTradabilityCodes(missingValues(requestedCodes, tradabilityCodes))
                .missingIndustrySeriesCodes(missingValues(requiredIndustryCodes, availableIndustryCodes))
                .build();
    }

    /**
     * 查询股票复权因子。
     *
     * @param stockCodes 股票代码
     * @param endDate 截止日期，包含当天
     * @param source 数据来源
     * @param sourceVersion 数据版本
     * @return 复权因子 DTO
     */
    public List<StockAdjustmentFactorDto> findAdjustmentFactors(Collection<String> stockCodes,
                                                                 LocalDate endDate,
                                                                 String source,
                                                                 String sourceVersion) {
        List<StockAdjustmentFactor> factors = endDate == null
                ? adjustmentFactorRepository
                .findByStockCodeInAndSourceAndSourceVersionOrderByStockCodeAscTradeDateAsc(
                        stockCodes, source, sourceVersion)
                : adjustmentFactorRepository
                .findByStockCodeInAndTradeDateLessThanEqualAndSourceAndSourceVersionOrderByStockCodeAscTradeDateAsc(
                        stockCodes, endDate, source, sourceVersion);
        return factors.stream().map(this::toAdjustmentFactorDto).toList();
    }

    /**
     * 查询历史股票池版本。
     *
     * @param stockCodes 股票代码
     * @param endDate 截止日期，包含当天
     * @param source 数据来源
     * @param sourceVersion 数据版本
     * @return 历史股票池 DTO
     */
    public List<HistoricalStockUniverseDto> findHistoricalUniverse(Collection<String> stockCodes,
                                                                    LocalDate endDate,
                                                                    String source,
                                                                    String sourceVersion) {
        List<HistoricalStockUniverseEntity> records = endDate == null
                ? historicalUniverseRepository
                .findByStockCodeInAndSourceAndSourceVersionOrderByStockCodeAscEffectiveFromAsc(
                        stockCodes, source, sourceVersion)
                : historicalUniverseRepository
                .findByStockCodeInAndEffectiveFromLessThanEqualAndSourceAndSourceVersionOrderByStockCodeAscEffectiveFromAsc(
                        stockCodes, endDate, source, sourceVersion);
        return records.stream().map(this::toHistoricalUniverseDto).toList();
    }

    /**
     * 查询市场或行业参考序列。
     *
     * @param seriesType 序列类型
     * @param seriesCode 序列代码
     * @param endDate 截止日期，包含当天
     * @param source 数据来源
     * @param sourceVersion 数据版本
     * @return 参考日线 DTO
     */
    public List<ModelReferenceDailyBarDto> findReferenceBars(String seriesType,
                                                              String seriesCode,
                                                              LocalDate endDate,
                                                              String source,
                                                              String sourceVersion) {
        ReferenceSeriesType type = ReferenceSeriesType.valueOf(seriesType);
        List<ModelReferenceDailyBar> bars = endDate == null
                ? modelReferenceDailyBarRepository
                .findBySeriesTypeAndSeriesCodeAndSourceAndSourceVersionOrderByTradeDateAsc(
                        type, seriesCode, source, sourceVersion)
                : modelReferenceDailyBarRepository
                .findBySeriesTypeAndSeriesCodeAndTradeDateLessThanEqualAndSourceAndSourceVersionOrderByTradeDateAsc(
                        type, seriesCode, endDate, source, sourceVersion);
        return bars.stream().map(this::toReferenceBarDto).toList();
    }

    /**
     * 查询最近市场参考序列。
     *
     * @return 最近市场参考日线 DTO
     */
    public List<ModelReferenceDailyBarDto> findLatestMarketReferenceBars() {
        return modelReferenceDailyBarRepository
                .findTop100BySeriesTypeOrderByTradeDateDesc(ReferenceSeriesType.MARKET)
                .stream().map(this::toReferenceBarDto).toList();
    }

    /**
     * 查询区间可成交性事实。
     *
     * @param stockCodes 股票代码
     * @param startDate 开始日期
     * @param endDate 结束日期
     * @param source 数据来源
     * @param sourceVersion 数据版本
     * @return 可成交性事实 DTO
     */
    public List<StockTradabilityDto> findTradabilityFacts(Collection<String> stockCodes,
                                                           LocalDate startDate,
                                                           LocalDate endDate,
                                                           String source,
                                                           String sourceVersion) {
        return tradabilityFactRepository
                .findByStockCodeInAndTradeDateBetweenAndSourceAndSourceVersion(
                        stockCodes, startDate, endDate, source, sourceVersion)
                .stream().map(this::toTradabilityDto).toList();
    }

    /**
     * 查询指定股票交易日最新可成交性事实。
     *
     * @param stockCode 股票代码
     * @param tradeDate 交易日期
     * @return 最新可成交性事实
     */
    public Optional<StockTradabilityDto> findTradability(String stockCode, LocalDate tradeDate) {
        return tradabilityFactRepository
                .findFirstByStockCodeAndTradeDateOrderByCapturedAtDesc(stockCode, tradeDate)
                .map(this::toTradabilityDto);
    }

    /**
     * 查询与指定区间重叠的历史股票池版本。
     *
     * @param startDate 开始日期
     * @param endDate 结束日期
     * @return 历史股票池 DTO
     */
    public List<HistoricalStockUniverseDto> findHistoricalUniverse(
            LocalDate startDate, LocalDate endDate) {
        Query query = Query.query(new Criteria().andOperator(
                Criteria.where("effectiveFrom").lte(endDate),
                new Criteria().orOperator(
                        Criteria.where("effectiveTo").is(null),
                        Criteria.where("effectiveTo").gte(startDate))))
                .with(Sort.by(Sort.Order.asc("stockCode"), Sort.Order.asc("effectiveFrom")));
        return mongoTemplate.find(query, HistoricalStockUniverseEntity.class).stream()
                .map(this::toHistoricalUniverseDto).toList();
    }

    /**
     * 查询指定股票和区间内的复权因子。
     *
     * @param stockCodes 股票代码
     * @param startDate 开始日期
     * @param endDate 结束日期
     * @return 复权因子 DTO
     */
    public List<StockAdjustmentFactorDto> findAdjustmentFactors(
            Collection<String> stockCodes, LocalDate startDate, LocalDate endDate) {
        Query query = Query.query(Criteria.where("stockCode").in(stockCodes)
                .and("tradeDate").gte(startDate).lte(endDate));
        return mongoTemplate.find(query, StockAdjustmentFactor.class).stream()
                .map(this::toAdjustmentFactorDto).toList();
    }

    private StockPriceItemDto toPriceDto(StockPrice price) {
        return StockPriceItemDto.builder().code(price.getCode()).stockName(price.getStockName())
                .date(price.getDate()).openPrice(price.getOpenPrice()).highPrice(price.getHighPrice())
                .lowPrice(price.getLowPrice()).closePrice(price.getClosePrice()).volume(price.getVolume())
                .amount(price.getAmount()).updateTime(price.getUpdateTime()).build();
    }

    private Set<String> distinctValues(Query query, String field, Class<?> entityType) {
        return new LinkedHashSet<>(mongoTemplate.findDistinct(query, field, entityType, String.class));
    }

    private Set<String> normalizedValues(Collection<String> values) {
        if (values == null) {
            return Set.of();
        }
        return values.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    }

    private int intersectionSize(Set<String> required, Set<String> actual) {
        return (int) required.stream().filter(actual::contains).count();
    }

    private List<String> missingValues(Set<String> required, Set<String> actual) {
        return required.stream().filter(value -> !actual.contains(value)).limit(20).toList();
    }

    private StockInfoItemDto toStockInfoDto(StockInfo stock) {
        return StockInfoItemDto.builder().id(stock.getId()).code(stock.getCode()).name(stock.getName())
                .market(stock.getMarket()).price(stock.getPrice()).changeAmount(stock.getChangeAmount())
                .changePercent(stock.getChangePercent()).volume(stock.getVolume())
                .totalMarketValue(stock.getTotalMarketValue()).turnoverRate(stock.getTurnoverRate())
                .volumeRatio(stock.getVolumeRatio()).industryCode(stock.getIndustryCode())
                .listedDate(stock.getListedDate()).updateTime(stock.getUpdateTime()).build();
    }

    private MarketNewsEventDto toNewsEventDto(StockNewsEventFact fact) {
        return MarketNewsEventDto.builder().id(fact.getId()).canonicalEventId(fact.getCanonicalEventId())
                .stockCode(fact.getStockCode()).stockName(fact.getStockName())
                .normalizedTitle(fact.getNormalizedTitle()).normalizedContent(fact.getNormalizedContent())
                .documentType(fact.getDocumentType()).documentRole(fact.getDocumentRole())
                .eventType(fact.getEventType()).contentHash(fact.getContentHash())
                .informationPriority(fact.getInformationPriority()).noveltyScore(fact.getNoveltyScore())
                .materialityScore(fact.getMaterialityScore()).stockSpecificity(fact.getStockSpecificity())
                .publishTime(fact.getPublishTime()).sourceExternalIds(fact.getSourceExternalIds())
                .sources(fact.getSources()).pipelineVersion(fact.getPipelineVersion()).build();
    }

    private MarketNewsDto toNewsDto(StockNews news) {
        return MarketNewsDto.builder().id(news.getId()).externalId(news.getExternalId())
                .title(news.getTitle()).source(news.getSource()).category(news.getCategory())
                .eventType(news.getEventType()).contentHash(news.getContentHash())
                .publishTime(news.getPublishTime()).build();
    }

    private ModelStockDailyBarDto toModelStockBarDto(StockPrice bar) {
        return ModelStockDailyBarDto.builder().id(bar.getId()).stockCode(bar.getCode())
                .tradeDate(bar.getDate())
                .openPrice(bar.getOpenPrice()).highPrice(bar.getHighPrice()).lowPrice(bar.getLowPrice())
                .closePrice(bar.getClosePrice()).preClosePrice(bar.getPreClosePrice())
                .volume(bar.getVolume()).amount(bar.getAmount())
                .turnoverRate(null).volumeUnit(bar.getVolumeUnit())
                .amountUnit(bar.getAmountUnit()).build();
    }

    private ModelReferenceDailyBarDto toReferenceBarDto(ModelReferenceDailyBar bar) {
        return ModelReferenceDailyBarDto.builder().seriesType(bar.getSeriesType().name())
                .seriesCode(bar.getSeriesCode()).tradeDate(bar.getTradeDate()).openPrice(bar.getOpenPrice())
                .highPrice(bar.getHighPrice()).lowPrice(bar.getLowPrice()).closePrice(bar.getClosePrice()).build();
    }

    private StockAdjustmentFactorDto toAdjustmentFactorDto(StockAdjustmentFactor factor) {
        return StockAdjustmentFactorDto.builder().stockCode(factor.getStockCode())
                .tradeDate(factor.getTradeDate()).forwardFactor(factor.getForwardFactor())
                .backwardFactor(factor.getBackwardFactor()).build();
    }

    private HistoricalStockUniverseDto toHistoricalUniverseDto(HistoricalStockUniverseEntity item) {
        return HistoricalStockUniverseDto.builder().stockCode(item.getStockCode()).stockName(item.getStockName())
                .market(item.getMarket()).boardCode(item.getBoardCode()).industryCode(item.getIndustryCode())
                .listedDate(item.getListedDate()).delistedDate(item.getDelistedDate()).stStatus(item.getStStatus())
                .effectiveFrom(item.getEffectiveFrom()).effectiveTo(item.getEffectiveTo()).build();
    }

    private StockTradabilityDto toTradabilityDto(StockDailyTradabilityFact fact) {
        return StockTradabilityDto.builder().stockCode(fact.getStockCode()).tradeDate(fact.getTradeDate())
                .openBuyStatus(fact.getOpenBuyStatus() == null ? null : fact.getOpenBuyStatus().name())
                .openSellStatus(fact.getOpenSellStatus() == null ? null : fact.getOpenSellStatus().name())
                .buyReason(fact.getBuyReason()).sellReason(fact.getSellReason())
                .capturedAt(fact.getCapturedAt()).build();
    }
}
// AI_GENERATE_END------------------
