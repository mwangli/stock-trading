// AI_GENERATE_START----
package com.stock.decision.service;

import com.stock.market.dto.HistoricalStockUniverseDto;
import com.stock.market.dto.StockAdjustmentFactorDto;
import com.stock.market.dto.StockPriceItemDto;
import com.stock.market.service.MarketDataQueryService;
import lombok.RequiredArgsConstructor;
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

    private final MarketDataQueryService marketDataQueryService;

    HistoricalUniverse loadHistoricalUniverse(LocalDate startDate, LocalDate endDate) {
        List<HistoricalStockUniverseDto> entities = marketDataQueryService
                .findHistoricalUniverse(startDate, endDate);
        if (entities.isEmpty()) {
            throw new IllegalStateException("生产数据门禁已放行，但历史股票池查询结果为空");
        }
        Map<String, List<ValidityWindow>> windowsByCode = entities.stream()
                .collect(Collectors.groupingBy(HistoricalStockUniverseDto::getStockCode,
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
        return marketDataQueryService.findTopStockCodes(universeLimit).stream()
                .filter(code -> code != null && !code.isBlank())
                .toList();
    }

    Map<String, StockSeries> loadSeries(List<String> stockCodes, LocalDate startDate,
                                        LocalDate endDate, boolean applyAdjustmentFactors) {
        Map<StockDateKey, BigDecimal> adjustmentFactors = applyAdjustmentFactors
                ? loadAdjustmentFactors(stockCodes, startDate, endDate) : Map.of();
        Map<String, StockSeries> result = new LinkedHashMap<>();
        for (String stockCode : stockCodes) {
            List<StockPriceItemDto> prices = marketDataQueryService.findPrices(stockCode, startDate, endDate)
                    .stream()
                    .filter(price -> price.getDate() != null)
                    .map(price -> applyAdjustmentFactors ? adjustedCopy(price, adjustmentFactors) : price)
                    .sorted(Comparator.comparing(StockPriceItemDto::getDate))
                    .toList();
            if (prices.isEmpty()) {
                continue;
            }
            Map<LocalDate, StockPriceItemDto> byDate = new HashMap<>();
            Map<LocalDate, Integer> indexByDate = new HashMap<>();
            for (int index = 0; index < prices.size(); index++) {
                StockPriceItemDto price = prices.get(index);
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
        return marketDataQueryService.findTradingDates(startDate, endDate);
    }

    List<LocalDate> collectTradingDates(Map<String, StockSeries> seriesByCode,
                                        LocalDate startDate, LocalDate endDate) {
        TreeSet<LocalDate> dates = new TreeSet<>();
        seriesByCode.values().forEach(series -> series.prices().stream()
                .map(StockPriceItemDto::getDate)
                .filter(date -> !date.isBefore(startDate) && !date.isAfter(endDate))
                .forEach(dates::add));
        return List.copyOf(dates);
    }

    private Map<StockDateKey, BigDecimal> loadAdjustmentFactors(
            List<String> stockCodes, LocalDate startDate, LocalDate endDate) {
        List<StockAdjustmentFactorDto> factors = marketDataQueryService
                .findAdjustmentFactors(stockCodes, startDate, endDate);
        Map<StockDateKey, BigDecimal> result = new HashMap<>();
        for (StockAdjustmentFactorDto factor : factors) {
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

    private StockPriceItemDto adjustedCopy(StockPriceItemDto source, Map<StockDateKey, BigDecimal> factors) {
        BigDecimal factor = factors.get(new StockDateKey(source.getCode(), source.getDate()));
        if (!isPositive(factor)) {
            throw new IllegalStateException("日线缺少对应前复权因子: "
                    + source.getCode() + " " + source.getDate());
        }
        StockPriceItemDto target = new StockPriceItemDto();
        target.setCode(source.getCode());
        target.setStockName(source.getStockName());
        target.setDate(source.getDate());
        target.setOpenPrice(adjustPrice(source.getOpenPrice(), factor));
        target.setHighPrice(adjustPrice(source.getHighPrice(), factor));
        target.setLowPrice(adjustPrice(source.getLowPrice(), factor));
        target.setClosePrice(adjustPrice(source.getClosePrice(), factor));
        target.setVolume(source.getVolume());
        target.setAmount(source.getAmount());
        target.setUpdateTime(source.getUpdateTime());
        return target;
    }

    private BigDecimal adjustPrice(BigDecimal price, BigDecimal factor) {
        return price == null ? null : price.multiply(factor).setScale(8, RoundingMode.HALF_UP);
    }

    private boolean isPositive(BigDecimal value) {
        return value != null && value.compareTo(BigDecimal.ZERO) > 0;
    }

    record StockSeries(String stockCode, List<StockPriceItemDto> prices,
                       Map<LocalDate, StockPriceItemDto> byDate,
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
// AI_GENERATE_END----
