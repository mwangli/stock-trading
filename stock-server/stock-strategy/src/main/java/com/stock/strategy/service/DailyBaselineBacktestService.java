// AI_GENERATE_START--------
package com.stock.strategy.service;

import com.stock.data.dto.StockPriceItemDto;
import com.stock.strategy.config.BacktestProperties;
import com.stock.data.dto.BacktestDataAuditDto;
import com.stock.data.service.BacktestDataAuditService;
import com.stock.strategy.dto.BacktestResultDto;
import com.stock.strategy.dto.BacktestTradeDto;
import com.stock.strategy.dto.DailyBaselineBacktestRequestDto;
import com.stock.strategy.entity.BacktestBaselineType;
import com.stock.strategy.service.BacktestMarketDataLoader.HistoricalUniverse;
import com.stock.strategy.service.BacktestMarketDataLoader.StockSeries;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;
import java.util.UUID;

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

    private static final String FEATURE_VERSION = "DAILY_T_MINUS_1_SIGNAL_V1";

    private final BacktestDataAuditService dataAuditService;
    private final BacktestProperties properties;
    private final BacktestMarketDataLoader marketDataLoader;

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
                ? marketDataLoader.loadHistoricalUniverse(loadStart, request.getEndDate()) : HistoricalUniverse.empty();
        List<String> stockCodes = marketDataLoader.resolveUniverse(
                request.getStockCodes(), universeLimit, productionDataFoundation, historicalUniverse);
        Map<String, StockSeries> seriesByCode = marketDataLoader.loadSeries(
                stockCodes, loadStart, request.getEndDate(), productionDataFoundation);
        List<LocalDate> tradingDates = productionDataFoundation
                ? marketDataLoader.findTradingDates(request.getStartDate(), request.getEndDate())
                : marketDataLoader.collectTradingDates(seriesByCode, request.getStartDate(), request.getEndDate());
        if (tradingDates.size() < 2) {
            throw new IllegalStateException("回测区间内不足两个可用交易日");
        }

        BacktestCapitalScenario gross5000 = BacktestCapitalScenario.gross(new BigDecimal("5000"));
        BacktestCapitalScenario net1000 = BacktestCapitalScenario.net(new BigDecimal("1000"));
        BacktestCapitalScenario net5000 = BacktestCapitalScenario.net(new BigDecimal("5000"));
        BacktestCapitalScenario net10000 = BacktestCapitalScenario.net(new BigDecimal("10000"));
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
            StockPriceItemDto signalBar = series.byDate().get(signalDate);
            StockPriceItemDto entryBar = series.byDate().get(entryDate);
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
            BacktestCapitalScenario.Trade trade1000 = net1000.execute(rawEntryPrice, rawExitPrice, properties, true);
            BacktestCapitalScenario.Trade trade5000 = net5000.execute(rawEntryPrice, rawExitPrice, properties, true);
            BacktestCapitalScenario.Trade trade10000 = net10000.execute(rawEntryPrice, rawExitPrice, properties, true);
            BacktestCapitalScenario.Trade grossTrade = gross5000.executeWithQuantity(
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
                                          BacktestCapitalScenario gross5000,
                                          BacktestCapitalScenario net1000,
                                          BacktestCapitalScenario net5000,
                                          BacktestCapitalScenario net10000,
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
            StockPriceItemDto signalBar = series.byDate().get(signalDate);
            StockPriceItemDto entryBar = series.byDate().get(entryDate);
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
            StockPriceItemDto bar = series.byDate().get(date);
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

    private record ExitPoint(LocalDate date, StockPriceItemDto bar, int tradingDateIndex) {
    }

}
// AI_GENERATE_END--------
