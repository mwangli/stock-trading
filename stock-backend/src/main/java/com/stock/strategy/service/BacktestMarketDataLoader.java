// AI_GENERATE_START--
package com.stock.strategy.service;

import com.stock.data.entity.HistoricalStockUniverseEntity;
import com.stock.data.entity.StockAdjustmentFactor;
import com.stock.data.entity.StockInfo;
import com.stock.data.entity.StockPrice;
import com.stock.data.repository.PriceRepository;
import com.stock.data.repository.StockInfoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * 日线回测市场数据加载器。
 *
 * 负责历史股票池、交易日、复权因子和日线序列的只读装载与一致性校验。
 *
 * @author mwangli
 * @since 2026-10-23
 */
@Component
@RequiredArgsConstructor
class BacktestMarketDataLoader {

    private final StockInfoRepository stockInfoRepository;
    private final PriceRepository priceRepository;
    private final MongoTemplate mongoTemplate;

    HistoricalUniverse loadHistoricalUniverse(LocalDate startDate, LocalDate endDate) {
        Query query = Query.query(new Criteria().andOperator(
                Criteria.where("effectiveFrom").lte(endDate),
                new Criteria().orOperator(
                        Criteria.where("effectiveTo").is(null),
                        Criteria.where("effectiveTo").gte(startDate))))
                .with(Sort.by(Sort.Order.asc("stockCode"), Sort.Order.asc("effectiveFrom")));
        List<HistoricalStockUniverseEntity> entities = mongoTemplate.find(
                query, HistoricalStockUniverseEntity.class);
        if (entities.isEmpty()) {
            throw new IllegalStateException("生产数据门禁已放行，但历史股票池查询结果为空");
        }
        Map<String, List<ValidityWindow>> windowsByCode = entities.stream()
                .collect(Collectors.groupingBy(HistoricalStockUniverseEntity::getStockCode,
                        LinkedHashMap::new,
                        Collectors.mapping(entity -> new ValidityWindow(
                                        entity.getEffectiveFrom(), entity.getEffectiveTo(),
                                        entity.getListedDate(), entity.getDelistedDate()),
                                Collectors.toList())));
        return new HistoricalUniverse(windowsByCode);
    }

    List<String> resolveUniverse(List<String> requestedCodes, int universeLimit,
                                 boolean productionDataFoundation, HistoricalUniverse historicalUniverse) {
        if (requestedCodes != null && !requestedCodes.isEmpty()) {
            List<String> resolved = requestedCodes.stream()
                    .filter(code -> code != null && !code.isBlank())
                    .map(String::trim)
                    .distinct()
                    .sorted()
                    .limit(universeLimit)
                    .toList();
            if (productionDataFoundation) {
                resolved = resolved.stream().filter(historicalUniverse::containsCode).toList();
            }
            if (resolved.isEmpty()) {
                throw new IllegalStateException("请求股票代码不在历史股票池有效范围内");
            }
            return resolved;
        }
        if (productionDataFoundation) {
            return historicalUniverse.codes().stream().sorted().limit(universeLimit).toList();
        }
        PageRequest page = PageRequest.of(0, universeLimit,
                Sort.by(Sort.Order.desc("totalMarketValue"), Sort.Order.asc("code")));
        return stockInfoRepository.findAll(page).getContent().stream()
                .map(StockInfo::getCode)
                .filter(code -> code != null && !code.isBlank())
                .toList();
    }

    Map<String, StockSeries> loadSeries(List<String> stockCodes, LocalDate startDate,
                                        LocalDate endDate, boolean applyAdjustmentFactors) {
        Map<StockDateKey, BigDecimal> adjustmentFactors = applyAdjustmentFactors
                ? loadAdjustmentFactors(stockCodes, startDate, endDate) : Map.of();
        Map<String, StockSeries> result = new LinkedHashMap<>();
        for (String stockCode : stockCodes) {
            List<StockPrice> prices = priceRepository
                    .findByCodeAndDateBetweenOrderByDateAsc(stockCode, startDate, endDate)
                    .stream()
                    .filter(price -> price.getDate() != null)
                    .map(price -> applyAdjustmentFactors ? adjustedCopy(price, adjustmentFactors) : price)
                    .sorted(Comparator.comparing(StockPrice::getDate))
                    .toList();
            if (prices.isEmpty()) {
                continue;
            }
            Map<LocalDate, StockPrice> byDate = new HashMap<>();
            Map<LocalDate, Integer> indexByDate = new HashMap<>();
            for (int index = 0; index < prices.size(); index++) {
                StockPrice price = prices.get(index);
                byDate.put(price.getDate(), price);
                indexByDate.put(price.getDate(), index);
            }
            result.put(stockCode, new StockSeries(stockCode, prices, byDate, indexByDate));
        }
        if (result.isEmpty()) {
            throw new IllegalStateException("选定股票池没有可用日线数据");
        }
        return result;
    }

    List<LocalDate> findTradingDates(LocalDate startDate, LocalDate endDate) {
        Query query = Query.query(Criteria.where("tradingDay").is(true)
                .and("tradeDate").gte(startDate).lte(endDate));
        return mongoTemplate.findDistinct(query, "tradeDate",
                        com.stock.data.entity.TradeCalendarEntity.class, LocalDate.class)
                .stream().sorted().toList();
    }

    List<LocalDate> collectTradingDates(Map<String, StockSeries> seriesByCode,
                                        LocalDate startDate, LocalDate endDate) {
        TreeSet<LocalDate> dates = new TreeSet<>();
        seriesByCode.values().forEach(series -> series.prices().stream()
                .map(StockPrice::getDate)
                .filter(date -> !date.isBefore(startDate) && !date.isAfter(endDate))
                .forEach(dates::add));
        return List.copyOf(dates);
    }

    private Map<StockDateKey, BigDecimal> loadAdjustmentFactors(
            List<String> stockCodes, LocalDate startDate, LocalDate endDate) {
        Query query = Query.query(Criteria.where("stockCode").in(stockCodes)
                .and("tradeDate").gte(startDate).lte(endDate));
        List<StockAdjustmentFactor> factors = mongoTemplate.find(query, StockAdjustmentFactor.class);
        Map<StockDateKey, BigDecimal> result = new HashMap<>();
        for (StockAdjustmentFactor factor : factors) {
            if (!isPositive(factor.getForwardFactor())) {
                throw new IllegalStateException("生产数据基础仅接受正数前复权因子: "
                        + factor.getStockCode() + " " + factor.getTradeDate());
            }
            StockDateKey key = new StockDateKey(factor.getStockCode(), factor.getTradeDate());
            BigDecimal existing = result.putIfAbsent(key, factor.getForwardFactor());
            if (existing != null && existing.compareTo(factor.getForwardFactor()) != 0) {
                throw new IllegalStateException("同一股票交易日存在冲突的前复权因子: "
                        + factor.getStockCode() + " " + factor.getTradeDate());
            }
        }
        return result;
    }

    private StockPrice adjustedCopy(StockPrice source, Map<StockDateKey, BigDecimal> factors) {
        BigDecimal factor = factors.get(new StockDateKey(source.getCode(), source.getDate()));
        if (!isPositive(factor)) {
            throw new IllegalStateException("日线缺少对应前复权因子: "
                    + source.getCode() + " " + source.getDate());
        }
        StockPrice target = new StockPrice();
        target.setId(source.getId());
        target.setCode(source.getCode());
        target.setStockName(source.getStockName());
        target.setDate(source.getDate());
        target.setOpenPrice(adjustPrice(source.getOpenPrice(), factor));
        target.setHighPrice(adjustPrice(source.getHighPrice(), factor));
        target.setLowPrice(adjustPrice(source.getLowPrice(), factor));
        target.setClosePrice(adjustPrice(source.getClosePrice(), factor));
        target.setVolume(source.getVolume());
        target.setAmount(source.getAmount());
        target.setCreateTime(source.getCreateTime());
        target.setUpdateTime(source.getUpdateTime());
        return target;
    }

    private BigDecimal adjustPrice(BigDecimal price, BigDecimal factor) {
        return price == null ? null : price.multiply(factor).setScale(8, RoundingMode.HALF_UP);
    }

    private boolean isPositive(BigDecimal value) {
        return value != null && value.compareTo(BigDecimal.ZERO) > 0;
    }

    record StockSeries(String stockCode, List<StockPrice> prices,
                       Map<LocalDate, StockPrice> byDate,
                       Map<LocalDate, Integer> indexByDate) {
    }

    private record StockDateKey(String stockCode, LocalDate tradeDate) {
    }

    private record ValidityWindow(LocalDate effectiveFrom, LocalDate effectiveTo,
                                  LocalDate listedDate, LocalDate delistedDate) {
        private boolean contains(LocalDate date) {
            boolean effective = !date.isBefore(effectiveFrom)
                    && (effectiveTo == null || !date.isAfter(effectiveTo));
            boolean listed = listedDate == null || !date.isBefore(listedDate);
            boolean notDelisted = delistedDate == null || date.isBefore(delistedDate);
            return effective && listed && notDelisted;
        }
    }

    record HistoricalUniverse(Map<String, List<ValidityWindow>> windowsByCode) {
        static HistoricalUniverse empty() {
            return new HistoricalUniverse(Map.of());
        }

        boolean isEmpty() {
            return windowsByCode.isEmpty();
        }

        boolean containsCode(String stockCode) {
            return windowsByCode.containsKey(stockCode);
        }

        boolean isActive(String stockCode, LocalDate date) {
            return windowsByCode.getOrDefault(stockCode, List.of()).stream()
                    .anyMatch(window -> window.contains(date));
        }

        List<String> codes() {
            return List.copyOf(windowsByCode.keySet());
        }
    }
}
// AI_GENERATE_END--
