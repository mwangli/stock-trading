// AI_GENERATE_START -------
package com.stock.strategyAnalysis.service;

import com.stock.dataCollector.domain.entity.HistoricalStockUniverseEntity;
import com.stock.dataCollector.domain.entity.StockAdjustmentFactor;
import com.stock.dataCollector.domain.entity.StockInfo;
import com.stock.dataCollector.domain.entity.StockPrice;
import com.stock.dataCollector.persistence.HistoricalStockUniverseRepository;
import com.stock.dataCollector.persistence.PriceRepository;
import com.stock.dataCollector.persistence.StockInfoRepository;
import com.stock.dataCollector.persistence.TradeCalendarRepository;
import com.stock.strategyAnalysis.config.BacktestProperties;
import com.stock.strategyAnalysis.domain.dto.BacktestDataAuditDto;
import com.stock.strategyAnalysis.domain.dto.BacktestResultDto;
import com.stock.strategyAnalysis.domain.dto.BacktestTradeDto;
import com.stock.strategyAnalysis.domain.dto.DailyBaselineBacktestRequestDto;
import com.stock.strategyAnalysis.domain.entity.BacktestBaselineType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 日线 T+1 固定成交基准服务。
 * 信号只使用 T 日之前的数据，T 日开盘买入，并在下一可卖交易日开盘退出。
 *
 * @author mwangli
 * @since 2026-09-25
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DailyBaselineBacktestService {

    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");
    private static final BigDecimal TEN_THOUSAND = new BigDecimal("10000");
    private static final String FEATURE_VERSION = "DAILY_T_MINUS_1_SIGNAL_V1";

    private final BacktestDataAuditService dataAuditService;
    private final StockInfoRepository stockInfoRepository;
    private final HistoricalStockUniverseRepository historicalStockUniverseRepository;
    private final TradeCalendarRepository tradeCalendarRepository;
    private final PriceRepository priceRepository;
    private final MongoTemplate mongoTemplate;
    private final BacktestProperties properties;

    /**
     * 执行明确允许的降级日线 T+1 基准。
     *
     * @param request 回测日期、候选基准和股票池参数
     * @return 毛收益、资金场景净收益和逐笔交易明细
     */
    public BacktestResultDto run(DailyBaselineBacktestRequestDto request) {
        validateRequest(request);
        long startedAt = System.currentTimeMillis();
        int universeLimit = valueOrDefault(request.getUniverseLimit(), properties.getUniverseLimit());
        int lookbackDays = valueOrDefault(request.getLookbackDays(), properties.getLookbackDays());
        long randomSeed = request.getRandomSeed() == null ? properties.getRandomSeed() : request.getRandomSeed();
        LocalDate loadStart = request.getStartDate().minusDays(Math.max(30L, lookbackDays * 4L));
        BacktestDataAuditDto audit = dataAuditService.audit(loadStart, request.getEndDate());
        if (!audit.isDailyBaselineRunnable()) {
            throw new IllegalStateException("当前日线数据覆盖不足，不能运行降级基准: " + audit.getBlockers());
        }
        if (!request.isAllowDegradedDailyBaseline()) {
            throw new IllegalStateException("日线固定开盘成交仍属于降级基准，必须显式确认允许降级");
        }

        boolean productionDataFoundation = audit.isProductionBacktestReady();
        HistoricalUniverse historicalUniverse = productionDataFoundation
                ? loadHistoricalUniverse(loadStart, request.getEndDate()) : HistoricalUniverse.empty();
        List<String> stockCodes = resolveUniverse(
                request.getStockCodes(), universeLimit, productionDataFoundation, historicalUniverse);
        Map<String, StockSeries> seriesByCode = loadSeries(
                stockCodes, loadStart, request.getEndDate(), productionDataFoundation);
        List<LocalDate> tradingDates = productionDataFoundation
                ? tradeCalendarRepository.findTradingDates(request.getStartDate(), request.getEndDate())
                : collectTradingDates(seriesByCode, request.getStartDate(), request.getEndDate());
        if (tradingDates.size() < 2) {
            throw new IllegalStateException("回测区间内不足两个可用交易日");
        }

        CapitalScenario gross5000 = CapitalScenario.gross(new BigDecimal("5000"));
        CapitalScenario net1000 = CapitalScenario.net(new BigDecimal("1000"));
        CapitalScenario net5000 = CapitalScenario.net(new BigDecimal("5000"));
        CapitalScenario net10000 = CapitalScenario.net(new BigDecimal("10000"));
        List<BacktestTradeDto> trades = new ArrayList<>();
        List<BigDecimal> realizedGrossReturns = new ArrayList<>();
        int blockedTrades = 0;
        int skippedTrades = 0;

        int dateIndex = 1;
        while (dateIndex < tradingDates.size() - 1) {
            LocalDate signalDate = tradingDates.get(dateIndex - 1);
            LocalDate entryDate = tradingDates.get(dateIndex);
            Candidate candidate = selectCandidate(request.getBaselineType(), signalDate, entryDate,
                    seriesByCode, lookbackDays, randomSeed, historicalUniverse);
            if (candidate == null) {
                dateIndex++;
                continue;
            }

            StockSeries series = seriesByCode.get(candidate.stockCode());
            StockPrice signalBar = series.byDate().get(signalDate);
            StockPrice entryBar = series.byDate().get(entryDate);
            LocalDate plannedExitDate = tradingDates.get(dateIndex + 1);
            if (!isPositive(entryBar.getOpenPrice()) || !isPositive(signalBar.getClosePrice())) {
                skippedTrades++;
                trades.add(blockedTrade(candidate.stockCode(), signalDate, entryDate, plannedExitDate,
                        "INVALID_ENTRY_PRICE", "买入开盘价或前一交易日收盘价无效"));
                dateIndex++;
                continue;
            }
            if (isBuyLimitLocked(entryBar.getOpenPrice(), signalBar.getClosePrice())) {
                skippedTrades++;
                trades.add(blockedTrade(candidate.stockCode(), signalDate, entryDate, plannedExitDate,
                        "ENTRY_LIMIT_UP", "T 日开盘达到回测配置的涨停阈值，按未成交处理"));
                dateIndex++;
                continue;
            }

            ExitPoint exitPoint = findExitPoint(series, tradingDates, dateIndex + 1);
            if (exitPoint == null) {
                blockedTrades++;
                trades.add(blockedTrade(candidate.stockCode(), signalDate, entryDate, plannedExitDate,
                        "EXIT_BLOCKED", "回测结束前没有找到可卖出的有效开盘价"));
                break;
            }

            BigDecimal rawEntryPrice = entryBar.getOpenPrice();
            BigDecimal rawExitPrice = exitPoint.bar().getOpenPrice();
            BigDecimal grossReturn = rawExitPrice.divide(rawEntryPrice, 10, RoundingMode.HALF_UP)
                    .subtract(BigDecimal.ONE);
            ScenarioTrade trade1000 = net1000.execute(rawEntryPrice, rawExitPrice, properties, true);
            ScenarioTrade trade5000 = net5000.execute(rawEntryPrice, rawExitPrice, properties, true);
            ScenarioTrade trade10000 = net10000.execute(rawEntryPrice, rawExitPrice, properties, true);
            ScenarioTrade grossTrade = gross5000.executeWithQuantity(
                    rawEntryPrice, rawExitPrice, trade5000.quantity());

            boolean anyScenarioExecuted = trade1000.quantity() >= 100
                    || trade5000.quantity() >= 100
                    || trade10000.quantity() >= 100;
            if (!anyScenarioExecuted) {
                skippedTrades++;
                trades.add(BacktestTradeDto.builder()
                        .stockCode(candidate.stockCode())
                        .signalDate(signalDate)
                        .entryDate(entryDate)
                        .plannedExitDate(plannedExitDate)
                        .actualExitDate(exitPoint.date())
                        .rawEntryPrice(scalePrice(rawEntryPrice))
                        .rawExitPrice(scalePrice(rawExitPrice))
                        .quantity(0)
                        .quantity1000(0)
                        .quantity10000(0)
                        .grossReturn(grossReturn.setScale(8, RoundingMode.HALF_UP))
                        .totalFee(BigDecimal.ZERO)
                        .netProfit(BigDecimal.ZERO)
                        .netProfit1000(BigDecimal.ZERO)
                        .netProfit10000(BigDecimal.ZERO)
                        .status("INSUFFICIENT_CAPITAL")
                        .reason("三个资金场景均不足以买入 100 股")
                        .build());
                dateIndex++;
                continue;
            }

            if (trade5000.quantity() < 100) {
                skippedTrades++;
            } else {
                realizedGrossReturns.add(grossReturn);
            }
            trades.add(BacktestTradeDto.builder()
                    .stockCode(candidate.stockCode())
                    .signalDate(signalDate)
                    .entryDate(entryDate)
                    .plannedExitDate(plannedExitDate)
                    .actualExitDate(exitPoint.date())
                    .rawEntryPrice(scalePrice(rawEntryPrice))
                    .rawExitPrice(scalePrice(rawExitPrice))
                    .executedEntryPrice(scalePrice(trade5000.entryPrice()))
                    .executedExitPrice(scalePrice(trade5000.exitPrice()))
                    .quantity(trade5000.quantity())
                    .quantity1000(trade1000.quantity())
                    .quantity10000(trade10000.quantity())
                    .grossReturn(grossReturn.setScale(8, RoundingMode.HALF_UP))
                    .totalFee(trade5000.totalFee())
                    .netProfit(trade5000.netProfit())
                    .netProfit1000(trade1000.netProfit())
                    .netProfit10000(trade10000.netProfit())
                    .status(trade5000.quantity() >= 100 ? "FILLED" : "INSUFFICIENT_CAPITAL")
                    .reason(exitPoint.date().equals(plannedExitDate)
                            ? "按 T+1 开盘退出" : "T+1 无法退出，延迟至下一可卖交易日")
                    .build());

            if (!exitPoint.date().equals(plannedExitDate)) {
                blockedTrades++;
            }
            dateIndex = Math.max(dateIndex + 1, exitPoint.tradingDateIndex());
            log.debug("[DailyBaseline] 完成交易: code={}, entry={}, exit={}, grossQty={}, net1000Qty={}, net10000Qty={}",
                    candidate.stockCode(), entryDate, exitPoint.date(), grossTrade.quantity(),
                    trade1000.quantity(), trade10000.quantity());
        }

        return buildResult(request, audit, stockCodes, gross5000, net1000, net5000, net10000,
                trades, realizedGrossReturns, blockedTrades, skippedTrades,
                System.currentTimeMillis() - startedAt, productionDataFoundation);
    }

    private BacktestResultDto buildResult(DailyBaselineBacktestRequestDto request,
                                          BacktestDataAuditDto audit,
                                          List<String> stockCodes,
                                          CapitalScenario gross5000,
                                          CapitalScenario net1000,
                                          CapitalScenario net5000,
                                          CapitalScenario net10000,
                                          List<BacktestTradeDto> trades,
                                          List<BigDecimal> grossReturns,
                                          int blockedTrades,
                                          int skippedTrades,
                                          long costTimeMs,
                                          boolean productionDataFoundation) {
        int winTrades = (int) grossReturns.stream().filter(value -> value.signum() > 0).count();
        double winRate = grossReturns.isEmpty() ? 0D : (double) winTrades / grossReturns.size();
        double avgProfit = averageBySign(grossReturns, true);
        double avgLoss = averageBySign(grossReturns, false);
        double totalReturn = gross5000.returnRate();
        long days = Math.max(1, ChronoUnit.DAYS.between(request.getStartDate(), request.getEndDate()) + 1);
        double annualizedReturn = totalReturn <= -1D
                ? -1D : Math.pow(1D + totalReturn, 365D / days) - 1D;
        List<String> limitations = buildLimitations(productionDataFoundation);
        return BacktestResultDto.builder()
                .resultId(UUID.randomUUID().toString())
                .startDate(request.getStartDate())
                .endDate(request.getEndDate())
                .totalReturn(totalReturn)
                .annualizedReturn(annualizedReturn)
                .maxDrawdown(net5000.maxDrawdown())
                .totalTrades(grossReturns.size())
                .winTrades(winTrades)
                .winRate(winRate)
                .avgProfit(avgProfit)
                .avgLoss(avgLoss)
                .avgHighCaptureRate(0D)
                .calculateTime(LocalDateTime.now().toString())
                .costTimeMs(costTimeMs)
                .baselineType(request.getBaselineType().name())
                .dataVersion((productionDataFoundation ? "ADJUSTED_" : "RAW_")
                        + "stock_prices:" + audit.getEarliestDailyDate() + ":"
                        + audit.getLatestDailyDate() + ":" + audit.getDailyRecordCount())
                .stockPoolVersion((productionDataFoundation
                        ? "HISTORICAL_UNIVERSE_SHA256:" : "CURRENT_STOCK_INFO_SHA256:")
                        + hashCodes(stockCodes))
                .featureVersion(FEATURE_VERSION)
                .feeVersion(properties.getFeeVersion())
                .codeVersion(properties.getCodeVersion())
                .netReturn1000(net1000.returnRate())
                .netReturn5000(net5000.returnRate())
                .netReturn10000(net10000.returnRate())
                .blockedTrades(blockedTrades)
                .skippedTrades(skippedTrades)
                .degraded(true)
                .limitations(limitations)
                .trades(List.copyOf(trades))
                .build();
    }

    private Candidate selectCandidate(BacktestBaselineType baselineType,
                                      LocalDate signalDate,
                                      LocalDate entryDate,
                                      Map<String, StockSeries> seriesByCode,
                                      int lookbackDays,
                                      long randomSeed,
                                      HistoricalUniverse historicalUniverse) {
        List<Candidate> candidates = new ArrayList<>();
        for (StockSeries series : seriesByCode.values()) {
            if (!historicalUniverse.isEmpty()
                    && !historicalUniverse.isActive(series.stockCode(), entryDate)) {
                continue;
            }
            StockPrice signalBar = series.byDate().get(signalDate);
            StockPrice entryBar = series.byDate().get(entryDate);
            if (signalBar == null || entryBar == null || !isPositive(entryBar.getOpenPrice())) {
                continue;
            }
            double score = calculateHistoricalScore(series, signalDate, lookbackDays);
            if ((baselineType == BacktestBaselineType.MOMENTUM
                    || baselineType == BacktestBaselineType.REVERSAL) && Double.isNaN(score)) {
                continue;
            }
            candidates.add(new Candidate(series.stockCode(), score));
        }
        if (candidates.isEmpty()) {
            return null;
        }
        candidates.sort(Comparator.comparing(Candidate::stockCode));
        return switch (baselineType) {
            case FIXED_CODE_ORDER -> candidates.get(0);
            case RANDOM -> candidates.get(new SplittableRandom(randomSeed ^ entryDate.toEpochDay())
                    .nextInt(candidates.size()));
            case MOMENTUM -> candidates.stream()
                    .max(Comparator.comparingDouble(Candidate::score)
                            .thenComparing(Candidate::stockCode))
                    .orElse(null);
            case REVERSAL -> candidates.stream()
                    .min(Comparator.comparingDouble(Candidate::score)
                            .thenComparing(Candidate::stockCode))
                    .orElse(null);
        };
    }

    private double calculateHistoricalScore(StockSeries series, LocalDate signalDate, int lookbackDays) {
        Integer signalIndex = series.indexByDate().get(signalDate);
        if (signalIndex == null || signalIndex - lookbackDays < 0) {
            return Double.NaN;
        }
        BigDecimal latestClose = series.prices().get(signalIndex).getClosePrice();
        BigDecimal baseClose = series.prices().get(signalIndex - lookbackDays).getClosePrice();
        if (!isPositive(latestClose) || !isPositive(baseClose)) {
            return Double.NaN;
        }
        return latestClose.divide(baseClose, 10, RoundingMode.HALF_UP)
                .subtract(BigDecimal.ONE).doubleValue();
    }

    private ExitPoint findExitPoint(StockSeries series, List<LocalDate> tradingDates, int startIndex) {
        for (int index = startIndex; index < tradingDates.size(); index++) {
            LocalDate date = tradingDates.get(index);
            StockPrice bar = series.byDate().get(date);
            if (bar == null || !isPositive(bar.getOpenPrice())) {
                continue;
            }
            Integer seriesIndex = series.indexByDate().get(date);
            if (seriesIndex == null || seriesIndex == 0) {
                continue;
            }
            BigDecimal previousClose = series.prices().get(seriesIndex - 1).getClosePrice();
            if (!isPositive(previousClose) || isSellLimitLocked(bar.getOpenPrice(), previousClose)) {
                continue;
            }
            return new ExitPoint(date, bar, index);
        }
        return null;
    }

    private Map<String, StockSeries> loadSeries(List<String> stockCodes,
                                                LocalDate startDate,
                                                LocalDate endDate,
                                                boolean applyAdjustmentFactors) {
        Map<StockDateKey, BigDecimal> adjustmentFactors = applyAdjustmentFactors
                ? loadAdjustmentFactors(stockCodes, startDate, endDate) : Map.of();
        Map<String, StockSeries> result = new LinkedHashMap<>();
        for (String stockCode : stockCodes) {
            List<StockPrice> prices = priceRepository
                    .findByCodeAndDateBetweenOrderByDateAsc(stockCode, startDate, endDate)
                    .stream()
                    .filter(price -> price.getDate() != null)
                    .map(price -> applyAdjustmentFactors
                            ? adjustedCopy(price, adjustmentFactors) : price)
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

    private Map<StockDateKey, BigDecimal> loadAdjustmentFactors(List<String> stockCodes,
                                                                 LocalDate startDate,
                                                                 LocalDate endDate) {
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

    private List<LocalDate> collectTradingDates(Map<String, StockSeries> seriesByCode,
                                                LocalDate startDate,
                                                LocalDate endDate) {
        TreeSet<LocalDate> dates = new TreeSet<>();
        seriesByCode.values().forEach(series -> series.prices().stream()
                .map(StockPrice::getDate)
                .filter(date -> !date.isBefore(startDate) && !date.isAfter(endDate))
                .forEach(dates::add));
        return List.copyOf(dates);
    }

    private List<String> resolveUniverse(List<String> requestedCodes, int universeLimit,
                                         boolean productionDataFoundation,
                                         HistoricalUniverse historicalUniverse) {
        if (requestedCodes != null && !requestedCodes.isEmpty()) {
            List<String> resolved = requestedCodes.stream()
                    .filter(code -> code != null && !code.isBlank())
                    .map(String::trim)
                    .distinct()
                    .sorted()
                    .limit(universeLimit)
                    .toList();
            if (productionDataFoundation) {
                resolved = resolved.stream()
                        .filter(historicalUniverse::containsCode)
                        .toList();
            }
            if (resolved.isEmpty()) {
                throw new IllegalStateException("请求股票代码不在历史股票池有效范围内");
            }
            return resolved;
        }
        if (productionDataFoundation) {
            return historicalUniverse.codes().stream()
                    .sorted()
                    .limit(universeLimit)
                    .toList();
        }
        PageRequest page = PageRequest.of(0, universeLimit,
                Sort.by(Sort.Order.desc("totalMarketValue"), Sort.Order.asc("code")));
        return stockInfoRepository.findAll(page).getContent().stream()
                .map(StockInfo::getCode)
                .filter(code -> code != null && !code.isBlank())
                .toList();
    }

    private HistoricalUniverse loadHistoricalUniverse(LocalDate startDate, LocalDate endDate) {
        List<HistoricalStockUniverseEntity> entities = historicalStockUniverseRepository
                .findOverlapping(startDate, endDate);
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

    private List<String> buildLimitations(boolean productionDataFoundation) {
        List<String> limitations = new ArrayList<>();
        if (!productionDataFoundation) {
            limitations.add("使用当前 stock_info 构建股票池，存在幸存者偏差");
            limitations.add("日线价格没有完整前复权因子覆盖，不能作为生产级五年回测结论");
            limitations.add("未使用独立交易日历，行情缺失和市场休市可能混淆");
            limitations.add("成交量单位未通过完整覆盖门禁，本基准不模拟部分成交和市场冲击");
        }
        limitations.add("使用日线开盘价模拟固定时点，尚未使用 5 分钟下一根 K 线成交");
        limitations.add("涨跌停统一使用配置的保守比例，尚未按历史板块规则逐日计算");
        limitations.add("最大回撤只按已实现交易后的资金计算，未包含隔夜持仓逐日盯市");
        return List.copyOf(limitations);
    }

    private boolean isBuyLimitLocked(BigDecimal openPrice, BigDecimal previousClose) {
        BigDecimal threshold = previousClose.multiply(BigDecimal.ONE.add(properties.getFallbackPriceLimitRate()));
        return openPrice.compareTo(threshold) >= 0;
    }

    private boolean isSellLimitLocked(BigDecimal openPrice, BigDecimal previousClose) {
        BigDecimal threshold = previousClose.multiply(BigDecimal.ONE.subtract(properties.getFallbackPriceLimitRate()));
        return openPrice.compareTo(threshold) <= 0;
    }

    private BacktestTradeDto blockedTrade(String stockCode, LocalDate signalDate,
                                           LocalDate entryDate, LocalDate plannedExitDate,
                                           String status, String reason) {
        return BacktestTradeDto.builder()
                .stockCode(stockCode)
                .signalDate(signalDate)
                .entryDate(entryDate)
                .plannedExitDate(plannedExitDate)
                .quantity(0)
                .quantity1000(0)
                .quantity10000(0)
                .grossReturn(BigDecimal.ZERO)
                .totalFee(BigDecimal.ZERO)
                .netProfit(BigDecimal.ZERO)
                .netProfit1000(BigDecimal.ZERO)
                .netProfit10000(BigDecimal.ZERO)
                .status(status)
                .reason(reason)
                .build();
    }

    private double averageBySign(List<BigDecimal> values, boolean positive) {
        return values.stream()
                .filter(value -> positive ? value.signum() > 0 : value.signum() < 0)
                .mapToDouble(BigDecimal::doubleValue)
                .average()
                .orElse(0D);
    }

    private String hashCodes(List<String> stockCodes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(String.join(",", stockCodes).getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("运行环境不支持 SHA-256", exception);
        }
    }

    private int valueOrDefault(Integer value, int defaultValue) {
        return value == null ? defaultValue : value;
    }

    private boolean isPositive(BigDecimal value) {
        return value != null && value.signum() > 0;
    }

    private BigDecimal scalePrice(BigDecimal value) {
        return value == null ? null : value.setScale(4, RoundingMode.HALF_UP);
    }

    private void validateRequest(DailyBaselineBacktestRequestDto request) {
        if (request == null || request.getStartDate() == null || request.getEndDate() == null
                || request.getBaselineType() == null) {
            throw new IllegalArgumentException("回测开始日期、结束日期和基准类型不能为空");
        }
        if (request.getStartDate().isAfter(request.getEndDate())) {
            throw new IllegalArgumentException("回测开始日期不能晚于结束日期");
        }
    }

    private record Candidate(String stockCode, double score) {
    }

    private record ExitPoint(LocalDate date, StockPrice bar, int tradingDateIndex) {
    }

    private record StockSeries(String stockCode, List<StockPrice> prices,
                               Map<LocalDate, StockPrice> byDate,
                               Map<LocalDate, Integer> indexByDate) {
    }

    private record StockDateKey(String stockCode, LocalDate tradeDate) {
    }

    private record ValidityWindow(LocalDate effectiveFrom, LocalDate effectiveTo,
                                  LocalDate listedDate, LocalDate delistedDate) {

        private boolean contains(LocalDate date) {
            return !effectiveFrom.isAfter(date)
                    && (effectiveTo == null || !effectiveTo.isBefore(date))
                    && (listedDate == null || !listedDate.isAfter(date))
                    && (delistedDate == null || !delistedDate.isBefore(date));
        }
    }

    private record HistoricalUniverse(Map<String, List<ValidityWindow>> windowsByCode) {

        private static HistoricalUniverse empty() {
            return new HistoricalUniverse(Map.of());
        }

        private boolean isEmpty() {
            return windowsByCode.isEmpty();
        }

        private boolean containsCode(String stockCode) {
            return windowsByCode.containsKey(stockCode);
        }

        private boolean isActive(String stockCode, LocalDate date) {
            return windowsByCode.getOrDefault(stockCode, List.of()).stream()
                    .anyMatch(window -> window.contains(date));
        }

        private List<String> codes() {
            return List.copyOf(windowsByCode.keySet());
        }
    }

    private record ScenarioTrade(int quantity, BigDecimal entryPrice, BigDecimal exitPrice,
                                 BigDecimal totalFee, BigDecimal netProfit) {
    }

    private static final class CapitalScenario {

        private final BigDecimal initialCapital;
        private final boolean includeCosts;
        private BigDecimal capital;
        private BigDecimal peakCapital;
        private BigDecimal maxDrawdown = BigDecimal.ZERO;

        private CapitalScenario(BigDecimal initialCapital, boolean includeCosts) {
            this.initialCapital = initialCapital;
            this.includeCosts = includeCosts;
            this.capital = initialCapital;
            this.peakCapital = initialCapital;
        }

        private static CapitalScenario gross(BigDecimal initialCapital) {
            return new CapitalScenario(initialCapital, false);
        }

        private static CapitalScenario net(BigDecimal initialCapital) {
            return new CapitalScenario(initialCapital, true);
        }

        private ScenarioTrade execute(BigDecimal rawEntryPrice, BigDecimal rawExitPrice,
                                      BacktestProperties properties, boolean applyCosts) {
            BigDecimal slippageRate = properties.getSlippageBps().divide(TEN_THOUSAND, 10, RoundingMode.HALF_UP);
            BigDecimal entryPrice = applyCosts
                    ? rawEntryPrice.multiply(BigDecimal.ONE.add(slippageRate)) : rawEntryPrice;
            BigDecimal exitPrice = applyCosts
                    ? rawExitPrice.multiply(BigDecimal.ONE.subtract(slippageRate)) : rawExitPrice;
            int quantity = affordableQuantity(entryPrice, properties, applyCosts);
            if (quantity < 100) {
                return new ScenarioTrade(0, entryPrice, exitPrice, BigDecimal.ZERO, BigDecimal.ZERO);
            }

            BigDecimal shares = BigDecimal.valueOf(quantity);
            BigDecimal buyAmount = entryPrice.multiply(shares);
            BigDecimal sellAmount = exitPrice.multiply(shares);
            BigDecimal buyFee = applyCosts ? buyFee(buyAmount, properties) : BigDecimal.ZERO;
            BigDecimal sellFee = applyCosts ? sellFee(sellAmount, properties) : BigDecimal.ZERO;
            BigDecimal netProfit = sellAmount.subtract(sellFee).subtract(buyAmount).subtract(buyFee);
            capital = capital.add(netProfit);
            if (capital.compareTo(peakCapital) > 0) {
                peakCapital = capital;
            }
            BigDecimal drawdown = peakCapital.signum() == 0 ? BigDecimal.ZERO
                    : peakCapital.subtract(capital).divide(peakCapital, 10, RoundingMode.HALF_UP);
            if (drawdown.compareTo(maxDrawdown) > 0) {
                maxDrawdown = drawdown;
            }
            return new ScenarioTrade(quantity, entryPrice, exitPrice,
                    buyFee.add(sellFee).setScale(2, RoundingMode.HALF_UP),
                    netProfit.setScale(2, RoundingMode.HALF_UP));
        }

        private ScenarioTrade executeWithQuantity(BigDecimal entryPrice, BigDecimal exitPrice, int quantity) {
            if (quantity < 100) {
                return new ScenarioTrade(0, entryPrice, exitPrice, BigDecimal.ZERO, BigDecimal.ZERO);
            }
            BigDecimal shares = BigDecimal.valueOf(quantity);
            BigDecimal profit = exitPrice.subtract(entryPrice).multiply(shares);
            capital = capital.add(profit);
            if (capital.compareTo(peakCapital) > 0) {
                peakCapital = capital;
            }
            BigDecimal drawdown = peakCapital.signum() == 0 ? BigDecimal.ZERO
                    : peakCapital.subtract(capital).divide(peakCapital, 10, RoundingMode.HALF_UP);
            if (drawdown.compareTo(maxDrawdown) > 0) {
                maxDrawdown = drawdown;
            }
            return new ScenarioTrade(quantity, entryPrice, exitPrice, BigDecimal.ZERO,
                    profit.setScale(2, RoundingMode.HALF_UP));
        }

        private int affordableQuantity(BigDecimal entryPrice, BacktestProperties properties,
                                       boolean applyCosts) {
            BigDecimal reservedFee = applyCosts ? properties.getMinimumCommission() : BigDecimal.ZERO;
            BigDecimal available = capital.subtract(reservedFee);
            if (available.signum() <= 0) {
                return 0;
            }
            int quantity = available.divide(entryPrice.multiply(ONE_HUNDRED), 0, RoundingMode.DOWN)
                    .intValue() * 100;
            while (quantity >= 100) {
                BigDecimal amount = entryPrice.multiply(BigDecimal.valueOf(quantity));
                BigDecimal fee = applyCosts ? buyFee(amount, properties) : BigDecimal.ZERO;
                if (amount.add(fee).compareTo(capital) <= 0) {
                    return quantity;
                }
                quantity -= 100;
            }
            return 0;
        }

        private BigDecimal buyFee(BigDecimal amount, BacktestProperties properties) {
            return commission(amount, properties)
                    .add(amount.multiply(properties.getTransferFeeRate()));
        }

        private BigDecimal sellFee(BigDecimal amount, BacktestProperties properties) {
            return commission(amount, properties)
                    .add(amount.multiply(properties.getStampDutyRate()))
                    .add(amount.multiply(properties.getTransferFeeRate()));
        }

        private BigDecimal commission(BigDecimal amount, BacktestProperties properties) {
            return amount.multiply(properties.getCommissionRate()).max(properties.getMinimumCommission());
        }

        private double returnRate() {
            return capital.divide(initialCapital, 10, RoundingMode.HALF_UP)
                    .subtract(BigDecimal.ONE).doubleValue();
        }

        private double maxDrawdown() {
            return includeCosts ? maxDrawdown.doubleValue() : 0D;
        }
    }
}
// AI_GENERATE_END -------
