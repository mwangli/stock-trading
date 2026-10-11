// AI_GENERATE_START -----
package com.stock.tradingExecutor.service;

import com.stock.tradingExecutor.domain.dto.PerformanceOverviewDto;
import com.stock.tradingExecutor.domain.dto.PerformancePeriodItemDto;
import com.stock.tradingExecutor.domain.dto.PerformanceRankingItemDto;
import com.stock.tradingExecutor.domain.entity.AccountSnapshotEntity;
import com.stock.tradingExecutor.domain.entity.TradeRecordEntity;
import com.stock.tradingExecutor.domain.entity.TradePerformanceSnapshotEntity;
import com.stock.tradingExecutor.persistence.AccountSnapshotRepository;
import com.stock.tradingExecutor.persistence.TradeRecordRepository;
import com.stock.tradingExecutor.persistence.TradePerformanceSnapshotRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 交易收益报表服务。
 * 账户资产读取最新资金快照，收益率、排行和周期收益读取 FIFO 派生交易记录。
 * 未卖出持仓不计入已实现收益，避免把浮动收益冒充为已实现收益。
 *
 * @author mwangli
 * @since 2026-10-11
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class PerformanceReportService {

    private static final int RATE_SCALE = 8;
    private static final int RANKING_LIMIT = 10;
    private static final DateTimeFormatter DAY_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DateTimeFormatter MONTH_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM");

    private final AccountSnapshotRepository accountSnapshotRepository;
    private final TradeRecordRepository tradeRecordRepository;
    private final TradePerformanceSnapshotRepository tradePerformanceSnapshotRepository;
    private final BrokerIdentityService brokerIdentityService;
    private final ObjectMapper objectMapper;

    /**
     * 查询当前账户的收益报表总览。
     *
     * @return 收益统计、排行、日收益和月收益
     */
    public PerformanceOverviewDto getOverview() {
        String brokerCode = brokerIdentityService.getBrokerCode();
        String accountId = brokerIdentityService.getAccountId();
        AccountSnapshotEntity account = accountSnapshotRepository
                .findFirstByBrokerCodeAndAccountIdOrderByCapturedAtDesc(brokerCode, accountId)
                .orElse(null);
        List<TradeRecordEntity> records = tradeRecordRepository
                .findByBrokerCodeAndAccountIdOrderByBuyTimeDesc(brokerCode, accountId);

        List<TradeRecordEntity> realizedRecords = records.stream()
                .filter(this::isRealized)
                .toList();
        BigDecimal cumulativeProfit = sumProfit(realizedRecords);
        BigDecimal cumulativeCost = sumCost(realizedRecords);
        LocalDate today = LocalDate.now();
        YearMonth currentMonth = YearMonth.now();
        List<TradeRecordEntity> dailyRecords = realizedRecords.stream()
                .filter(record -> sameDate(record.getLastSellTime(), today))
                .toList();
        List<TradeRecordEntity> monthlyRecords = realizedRecords.stream()
                .filter(record -> sameMonth(record.getLastSellTime(), currentMonth))
                .toList();

        return PerformanceOverviewDto.builder()
                .capturedAt(account != null ? account.getCapturedAt() : null)
                .totalAssets(account != null ? account.getTotalAssets() : BigDecimal.ZERO)
                .availableCash(account != null ? account.getAvailableCash() : BigDecimal.ZERO)
                .marketValue(account != null ? account.getMarketValue() : BigDecimal.ZERO)
                .frozenCash(account != null ? account.getFrozenCash() : BigDecimal.ZERO)
                .cumulativeProfit(cumulativeProfit)
                .cumulativeReturnRate(rate(cumulativeProfit, cumulativeCost))
                .dailyProfit(sumProfit(dailyRecords))
                .dailyReturnRate(rate(sumProfit(dailyRecords), sumCost(dailyRecords)))
                .monthlyProfit(sumProfit(monthlyRecords))
                .monthlyReturnRate(rate(sumProfit(monthlyRecords), sumCost(monthlyRecords)))
                .totalTradeCount(records.size())
                .closedTradeCount(realizedRecords.size())
                .winningTradeCount(countWinning(realizedRecords))
                .losingTradeCount(countLosing(realizedRecords))
                .winRate(rate(countWinning(realizedRecords), realizedRecords.size()))
                .averageReturnRate(averageRate(realizedRecords))
                .bestReturnRate(extremeRate(realizedRecords, true))
                .worstReturnRate(extremeRate(realizedRecords, false))
                .ranking(buildRanking(realizedRecords))
                .dailyReturns(buildPeriodItems(realizedRecords, true))
                .monthlyReturns(buildPeriodItems(realizedRecords, false))
                .build();
    }

    /**
     * 按当前交易记录生成并保存当日收益统计快照。
     * 同一天重复执行会覆盖同一账户同一日期的快照，不产生重复数据。
     */
    @Transactional
    public void refreshDailySnapshot() {
        String brokerCode = brokerIdentityService.getBrokerCode();
        String accountId = brokerIdentityService.getAccountId();
        PerformanceOverviewDto overview = getOverview();
        LocalDate today = LocalDate.now();
        TradePerformanceSnapshotEntity snapshot = tradePerformanceSnapshotRepository
                .findByBrokerCodeAndAccountIdAndAsOfDate(brokerCode, accountId, today)
                .orElseGet(() -> new TradePerformanceSnapshotEntity()
                        .setBrokerCode(brokerCode)
                        .setAccountId(accountId)
                        .setAsOfDate(today));
        snapshot.setCumulativeProfit(zero(overview.getCumulativeProfit()))
                .setCumulativeReturnRate(zero(overview.getCumulativeReturnRate()))
                .setDailyProfit(zero(overview.getDailyProfit()))
                .setDailyReturnRate(zero(overview.getDailyReturnRate()))
                .setMonthlyProfit(zero(overview.getMonthlyProfit()))
                .setMonthlyReturnRate(zero(overview.getMonthlyReturnRate()))
                .setTotalTradeCount(overview.getTotalTradeCount())
                .setClosedTradeCount(overview.getClosedTradeCount())
                .setWinningTradeCount(overview.getWinningTradeCount())
                .setLosingTradeCount(overview.getLosingTradeCount())
                .setWinRate(zero(overview.getWinRate()))
                .setAverageReturnRate(zero(overview.getAverageReturnRate()))
                .setBestReturnRate(zero(overview.getBestReturnRate()))
                .setWorstReturnRate(zero(overview.getWorstReturnRate()))
                .setRankingJson(toJson(overview.getRanking()))
                .setDailyReturnsJson(toJson(overview.getDailyReturns()))
                .setMonthlyReturnsJson(toJson(overview.getMonthlyReturns()));
        tradePerformanceSnapshotRepository.save(snapshot);
        log.info("[Performance] 已保存每日收益统计快照: date={}, cumulativeProfit={}, tradeCount={}",
                today, snapshot.getCumulativeProfit(), snapshot.getTotalTradeCount());
    }

    private List<PerformanceRankingItemDto> buildRanking(List<TradeRecordEntity> records) {
        return records.stream()
                .sorted(Comparator.comparing(this::safeReturnRate).reversed())
                .limit(RANKING_LIMIT)
                .map(record -> PerformanceRankingItemDto.builder()
                        .stockCode(record.getStockCode())
                        .stockName(record.getStockName())
                        .status(record.getStatus())
                        .netProfit(zero(record.getNetProfit()))
                        .returnRate(zero(record.getReturnRate()))
                        .dailyReturnRate(zero(record.getDailyReturnRate()))
                        .holdingDays(zero(record.getHoldingDays()))
                        .sellTime(record.getLastSellTime())
                        .build())
                .toList();
    }

    private List<PerformancePeriodItemDto> buildPeriodItems(List<TradeRecordEntity> records, boolean daily) {
        Map<String, PeriodAccumulator> grouped = new LinkedHashMap<>();
        records.stream()
                .sorted(Comparator.comparing(TradeRecordEntity::getLastSellTime))
                .forEach(record -> {
                    String period = formatPeriod(record.getLastSellTime(), daily);
                    PeriodAccumulator accumulator = grouped.computeIfAbsent(period, ignored -> new PeriodAccumulator());
                    accumulator.count++;
                    accumulator.netProfit = accumulator.netProfit.add(zero(record.getNetProfit()));
                    accumulator.cost = accumulator.cost.add(cost(record));
                    if (safeReturnRate(record).compareTo(BigDecimal.ZERO) > 0) {
                        accumulator.winningCount++;
                    }
                });
        return grouped.entrySet().stream()
                .sorted(Map.Entry.<String, PeriodAccumulator>comparingByKey().reversed())
                .limit(12)
                .map(entry -> PerformancePeriodItemDto.builder()
                        .period(entry.getKey())
                        .netProfit(entry.getValue().netProfit)
                        .returnRate(rate(entry.getValue().netProfit, entry.getValue().cost))
                        .tradeCount(entry.getValue().count)
                        .winningTradeCount(entry.getValue().winningCount)
                        .build())
                .toList();
    }

    private boolean isRealized(TradeRecordEntity record) {
        return record.getSoldQuantity() != null
                && record.getSoldQuantity() > 0
                && record.getLastSellTime() != null;
    }

    private boolean sameDate(LocalDateTime value, LocalDate target) {
        return value != null && value.toLocalDate().equals(target);
    }

    private boolean sameMonth(LocalDateTime value, YearMonth target) {
        return value != null && YearMonth.from(value).equals(target);
    }

    private String formatPeriod(LocalDateTime value, boolean daily) {
        return daily ? value.toLocalDate().format(DAY_FORMATTER) : YearMonth.from(value).format(MONTH_FORMATTER);
    }

    private BigDecimal sumProfit(List<TradeRecordEntity> records) {
        return records.stream().map(record -> zero(record.getNetProfit()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal sumCost(List<TradeRecordEntity> records) {
        return records.stream().map(this::cost).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal cost(TradeRecordEntity record) {
        return zero(record.getSoldCostAmount()).add(zero(record.getAllocatedBuyFee()));
    }

    private long countWinning(List<TradeRecordEntity> records) {
        return records.stream().filter(record -> safeReturnRate(record).compareTo(BigDecimal.ZERO) > 0).count();
    }

    private long countLosing(List<TradeRecordEntity> records) {
        return records.stream().filter(record -> safeReturnRate(record).compareTo(BigDecimal.ZERO) < 0).count();
    }

    private BigDecimal averageRate(List<TradeRecordEntity> records) {
        if (records.isEmpty()) {
            return BigDecimal.ZERO.setScale(RATE_SCALE);
        }
        return records.stream().map(this::safeReturnRate).reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(records.size()), RATE_SCALE, RoundingMode.HALF_UP);
    }

    private BigDecimal extremeRate(List<TradeRecordEntity> records, boolean maximum) {
        return records.stream().map(this::safeReturnRate)
                .reduce((left, right) -> maximum
                        ? left.max(right)
                        : left.min(right))
                .orElse(BigDecimal.ZERO.setScale(RATE_SCALE));
    }

    private BigDecimal safeReturnRate(TradeRecordEntity record) {
        return zero(record.getReturnRate());
    }

    private BigDecimal rate(BigDecimal numerator, BigDecimal denominator) {
        if (denominator == null || denominator.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO.setScale(RATE_SCALE);
        }
        return numerator.divide(denominator, RATE_SCALE, RoundingMode.HALF_UP);
    }

    private BigDecimal rate(long numerator, long denominator) {
        if (denominator == 0) {
            return BigDecimal.ZERO.setScale(RATE_SCALE);
        }
        return BigDecimal.valueOf(numerator).divide(BigDecimal.valueOf(denominator), RATE_SCALE, RoundingMode.HALF_UP);
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("收益统计明细序列化失败", exception);
        }
    }

    private BigDecimal zero(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }

    private static final class PeriodAccumulator {
        private BigDecimal netProfit = BigDecimal.ZERO;
        private BigDecimal cost = BigDecimal.ZERO;
        private long count;
        private long winningCount;
    }
}
// AI_GENERATE_END -----
