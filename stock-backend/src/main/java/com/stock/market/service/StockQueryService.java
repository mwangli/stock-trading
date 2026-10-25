// AI_GENERATE_START--------
package com.stock.market.service;

import com.stock.platform.config.CacheNames;
import com.stock.market.dto.StockDataValidationResponseDto;
import com.stock.market.dto.StockInfoItemDto;
import com.stock.market.dto.StockInfoListResponseDto;
import com.stock.market.dto.StockListPageResponseDto;
import com.stock.market.dto.StockStatisticsResponseDto;
import com.stock.market.dto.TopIncreaseItemDto;
import com.stock.market.dto.TopIncreaseListResponseDto;
import com.stock.market.entity.StockInfo;
import com.stock.market.repository.StockInfoRepository;
import com.stock.market.dto.MarketStatsDto;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * 股票基础信息查询服务。
 *
 * 负责分页、统计、完整性校验、涨幅榜和市场概览等只读场景。
 *
 * @author mwangli
 * @since 2026-10-23
 */
@Service
@RequiredArgsConstructor
public class StockQueryService {

    private final StockInfoRepository stockInfoRepository;
    private final MongoTemplate mongoTemplate;
    private final TradingCalendarService tradingCalendarService;

    /**
     * 查询股票信息页面的分页数据。
     *
     * @param current 当前页码，从 1 开始
     * @param pageSize 每页记录数
     * @param keywords 股票代码或名称关键字
     * @param sortBy 排序字段
     * @param sortOrder 排序方向
     * @return 股票分页业务数据
     */
    public StockInfoListResponseDto queryStockInfoPage(int current,
                                                       int pageSize,
                                                       String keywords,
                                                       String sortBy,
                                                       String sortOrder) {
        int safeCurrent = Math.max(1, current);
        int safePageSize = Math.max(1, pageSize);
        PageRequest pageRequest = PageRequest.of(
                safeCurrent - 1, safePageSize, resolveSort(sortBy, sortOrder));
        Page<StockInfo> page = queryTradablePage(keywords, pageRequest);
        return StockInfoListResponseDto.builder()
                .items(page.getContent().stream().map(this::toItemDto).toList())
                .total(page.getTotalElements())
                .current(page.getNumber() + 1)
                .pageSize(page.getSize())
                .build();
    }

    /**
     * 查询股票数据管理页面的分页数据。
     *
     * @param page 当前页码，从 1 开始
     * @param size 每页记录数
     * @return 股票分页业务数据
     */
    public StockListPageResponseDto queryStockDataPage(int page, int size) {
        int safePage = Math.max(1, page);
        int safeSize = Math.max(1, size);
        Page<StockInfo> result = stockInfoRepository.findAll(
                PageRequest.of(safePage - 1, safeSize, Sort.by(Sort.Direction.ASC, "code")));
        return StockListPageResponseDto.builder()
                .items(result.getContent().stream().map(this::toItemDto).toList())
                .total(result.getTotalElements())
                .page(result.getNumber() + 1)
                .size(result.getSize())
                .build();
    }

    /**
     * 根据股票代码查询基础信息。
     *
     * @param code 股票代码
     * @return 股票信息，不存在时为空
     */
    @Cacheable(cacheNames = CacheNames.STOCK_INFO, key = "#code",
            unless = "#result == null || #result.isEmpty()")
    public Optional<StockInfoItemDto> findByCode(String code) {
        return stockInfoRepository.findByCode(code).map(this::toItemDto);
    }

    /**
     * 查询全部可交易股票代码。
     *
     * @return 可交易股票代码列表
     */
    public List<String> findAllCodes() {
        return stockInfoRepository.findAllCodes();
    }

    /**
     * 查询股票基础信息总数。
     *
     * @return 当前已保存的股票数量
     */
    public long countStocks() {
        return stockInfoRepository.count();
    }

    /**
     * 汇总股票数据完整性统计。
     *
     * @return 字段完整率和市场分布
     */
    public StockStatisticsResponseDto getStatistics() {
        List<StockInfo> stocks = stockInfoRepository.findAll();
        long totalCount = stocks.size();
        Map<String, Long> marketDistribution = new HashMap<>();
        Map<String, Long> completeness = new HashMap<>();
        for (StockInfo stock : stocks) {
            String market = stock.getMarket() == null || stock.getMarket().isBlank() ? "未知" : stock.getMarket();
            marketDistribution.merge(market, 1L, Long::sum);
            increment(completeness, "name", stock.getName() != null && !stock.getName().isBlank());
            increment(completeness, "price", stock.getPrice() != null);
            increment(completeness, "market", stock.getMarket() != null && !stock.getMarket().isBlank());
            increment(completeness, "changePercent", stock.getChangePercent() != null);
            increment(completeness, "changeAmount", stock.getChangeAmount() != null);
            increment(completeness, "volume", stock.getVolume() != null);
            increment(completeness, "totalMarketValue", stock.getTotalMarketValue() != null);
            increment(completeness, "turnoverRate", stock.getTurnoverRate() != null);
            increment(completeness, "volumeRatio", stock.getVolumeRatio() != null);
            increment(completeness, "bidAsk", stock.getBidPrice() != null && stock.getAskPrice() != null);
            increment(completeness, "combinationSignals",
                    stock.getVolumePriceSignal() != null && stock.getMomentumStrength() != null);
        }
        Map<String, String> completenessRate = new HashMap<>();
        completeness.forEach((field, count) -> completenessRate.put(field, formatRate(count, totalCount)));
        return StockStatisticsResponseDto.builder()
                .totalCount(totalCount)
                .marketDistribution(Map.copyOf(marketDistribution))
                .fieldCompleteness(Map.copyOf(completeness))
                .completenessRate(Map.copyOf(completenessRate))
                .build();
    }

    /**
     * 校验股票数据关键字段完整性。
     *
     * @return 缺失字段统计和总体质量
     */
    public StockDataValidationResponseDto validateData() {
        List<StockInfo> stocks = stockInfoRepository.findAll();
        Map<String, Integer> missing = new HashMap<>();
        for (StockInfo stock : stocks) {
            incrementMissing(missing, "missingCode", stock.getCode() == null || stock.getCode().isBlank());
            incrementMissing(missing, "missingName", stock.getName() == null || stock.getName().isBlank());
            incrementMissing(missing, "missingPrice", stock.getPrice() == null);
            incrementMissing(missing, "missingMarket", stock.getMarket() == null || stock.getMarket().isBlank());
            incrementMissing(missing, "missingChangePercent", stock.getChangePercent() == null);
            incrementMissing(missing, "missingTotalMarketValue", stock.getTotalMarketValue() == null);
            incrementMissing(missing, "missingTurnoverRate", stock.getTurnoverRate() == null);
        }
        int missingNames = missing.getOrDefault("missingName", 0);
        return StockDataValidationResponseDto.builder()
                .totalRecords(stocks.size())
                .validation(Map.copyOf(missing))
                .valid(missing.getOrDefault("missingCode", 0) == 0)
                .dataQuality(formatRate(stocks.size() - missingNames, stocks.size()))
                .build();
    }

    /**
     * 查询可交易股票涨幅榜前十名。
     *
     * @return 涨幅榜业务数据
     */
    public TopIncreaseListResponseDto getTopIncreaseList() {
        Query query = Query.query(marketVisibleCriteria().and("changePercent").ne(null))
                .with(Sort.by(Sort.Direction.DESC, "changePercent"))
                .limit(10);
        List<TopIncreaseItemDto> items = mongoTemplate.find(query, StockInfo.class).stream()
                .map(stock -> TopIncreaseItemDto.builder()
                        .code(stock.getCode())
                        .name(stock.getName())
                        .changePercent(stock.getChangePercent())
                        .build())
                .toList();
        return TopIncreaseListResponseDto.builder().items(items).build();
    }

    /**
     * 汇总当前市场状态和涨跌分布。
     *
     * @return 市场统计数据
     */
    public MarketStatsDto getMarketStats() {
        List<StockInfo> tradableStocks = stockInfoRepository.findAll().stream()
                .filter(this::isMarketVisibleStock)
                .toList();
        long totalCount = tradableStocks.size();
        if (totalCount == 0) {
            return emptyMarketStats();
        }

        LocalDate today = LocalDate.now();
        LocalTime now = LocalTime.now();
        boolean marketOpen = tradingCalendarService.isTradingDay(today)
                && !now.isBefore(LocalTime.of(9, 30))
                && now.isBefore(LocalTime.of(15, 0));
        int upCount = (int) tradableStocks.stream().filter(item -> positive(item.getChangePercent())).count();
        int downCount = (int) tradableStocks.stream().filter(item -> negative(item.getChangePercent())).count();
        BigDecimal totalAmount = sum(tradableStocks, StockInfo::getTotalMarketValue);
        BigDecimal sumTurnover = sum(tradableStocks, StockInfo::getTurnoverRate);
        BigDecimal changeSum = sum(tradableStocks, StockInfo::getChangePercent);
        long changeCount = tradableStocks.stream().map(StockInfo::getChangePercent).filter(java.util.Objects::nonNull).count();
        StockInfo topGainer = tradableStocks.stream().filter(item -> item.getChangePercent() != null)
                .max(Comparator.comparing(StockInfo::getChangePercent)).orElse(null);
        StockInfo topLoser = tradableStocks.stream().filter(item -> item.getChangePercent() != null)
                .min(Comparator.comparing(StockInfo::getChangePercent)).orElse(null);

        return MarketStatsDto.builder()
                .marketStatus(marketOpen ? "开盘" : "休市")
                .changePercent(changeCount == 0 ? BigDecimal.ZERO
                        : changeSum.divide(BigDecimal.valueOf(changeCount), 2, RoundingMode.HALF_UP))
                .upCount(upCount)
                .downCount(downCount)
                .flatCount((int) totalCount - upCount - downCount)
                .totalAmount(totalAmount)
                .totalVolume(BigDecimal.ZERO)
                .totalCount((int) totalCount)
                .avgTurnoverRate(sumTurnover.divide(BigDecimal.valueOf(totalCount), 4, RoundingMode.HALF_UP))
                .topGainerCode(topGainer == null ? null : topGainer.getCode())
                .topGainerName(topGainer == null ? null : topGainer.getName())
                .topGainerChange(topGainer == null ? null : topGainer.getChangePercent())
                .topLoserCode(topLoser == null ? null : topLoser.getCode())
                .topLoserName(topLoser == null ? null : topLoser.getName())
                .topLoserChange(topLoser == null ? null : topLoser.getChangePercent())
                .build();
    }

    private Page<StockInfo> queryTradablePage(String keywords, PageRequest pageRequest) {
        List<Criteria> conditions = new java.util.ArrayList<>();
        conditions.add(marketVisibleCriteria());
        if (keywords != null && !keywords.isBlank()) {
            Pattern pattern = containsPattern(keywords);
            conditions.add(new Criteria().orOperator(
                    Criteria.where("name").regex(pattern),
                    Criteria.where("code").regex(pattern)));
        }
        Query query = Query.query(new Criteria().andOperator(conditions));
        long total = mongoTemplate.count(query, StockInfo.class);
        List<StockInfo> content = mongoTemplate.find(query.with(pageRequest), StockInfo.class);
        return new org.springframework.data.domain.PageImpl<>(content, pageRequest, total);
    }

    private Sort resolveSort(String sortBy, String sortOrder) {
        String property = switch (sortBy == null ? "" : sortBy.trim()) {
            case "price" -> "price";
            case "change" -> "changePercent";
            case "marketCap" -> "totalMarketValue";
            default -> "updateTime";
        };
        Sort.Direction direction = "asc".equalsIgnoreCase(sortOrder) ? Sort.Direction.ASC : Sort.Direction.DESC;
        return Sort.by(new Sort.Order(direction, property), new Sort.Order(Sort.Direction.DESC, "id"));
    }

    private StockInfoItemDto toItemDto(StockInfo stock) {
        return StockInfoItemDto.builder()
                .id(stock.getId())
                .code(stock.getCode())
                .name(stock.getName())
                .market(stock.getMarket())
                .price(stock.getPrice())
                .changeAmount(stock.getChangeAmount())
                .changePercent(stock.getChangePercent())
                .volume(stock.getVolume())
                .totalMarketValue(stock.getTotalMarketValue())
                .turnoverRate(stock.getTurnoverRate())
                .volumeRatio(stock.getVolumeRatio())
                .industryCode(stock.getIndustryCode())
                .listedDate(stock.getListedDate())
                .updateTime(stock.getUpdateTime())
                .build();
    }

    private MarketStatsDto emptyMarketStats() {
        return MarketStatsDto.builder()
                .marketStatus("休市")
                .changePercent(BigDecimal.ZERO)
                .upCount(0)
                .downCount(0)
                .flatCount(0)
                .totalAmount(BigDecimal.ZERO)
                .totalVolume(BigDecimal.ZERO)
                .totalCount(0)
                .avgTurnoverRate(BigDecimal.ZERO)
                .build();
    }

    private BigDecimal sum(List<StockInfo> stocks,
                           java.util.function.Function<StockInfo, BigDecimal> getter) {
        return stocks.stream().map(getter).filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private void increment(Map<String, Long> values, String key, boolean present) {
        values.putIfAbsent(key, 0L);
        if (present) {
            values.computeIfPresent(key, (ignored, count) -> count + 1);
        }
    }

    private void incrementMissing(Map<String, Integer> values, String key, boolean missing) {
        values.putIfAbsent(key, 0);
        if (missing) {
            values.computeIfPresent(key, (ignored, count) -> count + 1);
        }
    }

    private String formatRate(long count, long total) {
        return String.format(Locale.ROOT, "%.2f%%", total > 0 ? count * 100.0 / total : 0D);
    }

    private Criteria tradableCriteria() {
        Pattern inactive = Pattern.compile("ST|退|DELIST|TERMINATED", Pattern.CASE_INSENSITIVE);
        return new Criteria().andOperator(
                Criteria.where("name").not().regex(inactive),
                Criteria.where("securityCategory").not().regex(inactive),
                Criteria.where("market").not().regex(inactive));
    }

    private Criteria marketVisibleCriteria() {
        return new Criteria().andOperator(
                tradableCriteria(),
                Criteria.where("price").gt(BigDecimal.ZERO),
                Criteria.where("totalMarketValue").gt(BigDecimal.ZERO));
    }

    private Pattern containsPattern(String value) {
        return Pattern.compile(Pattern.quote(value.trim()), Pattern.CASE_INSENSITIVE);
    }

    private boolean isMarketVisibleStock(StockInfo stock) {
        return positive(stock.getPrice())
                && positive(stock.getTotalMarketValue())
                && isActiveText(stock.getName())
                && isActiveText(stock.getSecurityCategory())
                && isActiveText(stock.getMarket());
    }

    private boolean isActiveText(String value) {
        if (value == null) {
            return true;
        }
        String normalized = value.toUpperCase(Locale.ROOT);
        return !normalized.contains("ST")
                && !value.contains("退")
                && !normalized.contains("DELIST")
                && !normalized.contains("TERMINATED");
    }

    private boolean positive(BigDecimal value) {
        return value != null && value.signum() > 0;
    }

    private boolean negative(BigDecimal value) {
        return value != null && value.signum() < 0;
    }
}
// AI_GENERATE_END--------
