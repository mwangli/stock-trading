// AI_GENERATE_START-------------------
package com.stock.trading.service;

import com.stock.trading.dto.PerformanceOverviewDto;
import com.stock.trading.dto.PerformanceDailyReturnItemDto;
import com.stock.trading.dto.PerformancePeriodItemDto;
import com.stock.trading.dto.PerformanceRankingItemDto;
import com.stock.trading.dto.PerformanceAnalysisDto;
import com.stock.trading.dto.PerformanceAnalysisItemDto;
import com.stock.trading.entity.AccountSnapshotEntity;
import com.stock.trading.entity.TradeRecordEntity;
import com.stock.trading.repository.AccountSnapshotRepository;
import com.stock.trading.repository.TradeRecordRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
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
    private static final DateTimeFormatter DAY_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DateTimeFormatter MONTH_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM");

    private final AccountSnapshotRepository accountSnapshotRepository;
    private final TradeRecordRepository tradeRecordRepository;

    /**
     * 查询当前账户的收益报表总览。
     *
     * @return 收益统计、排行、日收益和月收益
     */
    public PerformanceOverviewDto getOverview() {
        AccountSnapshotEntity account = getLatestAccountSnapshot();
        String brokerCode = account.getBrokerCode();
        String accountId = account.getAccountId();
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
                .capturedAt(account.getCapturedAt())
                .totalAssets(zero(account.getTotalAssets()))
                .availableCash(zero(account.getAvailableCash()))
                .marketValue(zero(account.getMarketValue()))
                .frozenCash(zero(account.getFrozenCash()))
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
                .dailyReturns(buildDailyItems(realizedRecords))
                .monthlyReturns(buildPeriodItems(realizedRecords, false))
                .build();
    }

    /**
     * 查询指定自然月的已实现收益和主要损益来源。
     *
     * @param yearMonth 查询自然月
     * @return 月度收益分析
     */
    public PerformanceAnalysisDto getAnalysis(YearMonth yearMonth) {
        if (yearMonth == null) {
            throw new IllegalArgumentException("yearMonth 不能为空");
        }
        AccountSnapshotEntity account = getLatestAccountSnapshot();
        String brokerCode = account.getBrokerCode();
        String accountId = account.getAccountId();
        List<TradeRecordEntity> records = tradeRecordRepository
                .findByBrokerCodeAndAccountIdOrderByBuyTimeDesc(brokerCode, accountId).stream()
                .filter(this::isRealized)
                .filter(record -> sameMonth(record.getLastSellTime(), yearMonth))
                .toList();
        BigDecimal grossProfit = records.stream().map(record -> zero(record.getGrossProfit()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal netProfit = sumProfit(records);
        BigDecimal totalFees = records.stream().map(this::fees).reduce(BigDecimal.ZERO, BigDecimal::add);
        long winningCount = countWinning(records);
        long losingCount = countLosing(records);
        List<PerformanceAnalysisItemDto> gains = records.stream()
                .filter(record -> zero(record.getNetProfit()).signum() > 0)
                .sorted(Comparator.comparing((TradeRecordEntity record) -> zero(record.getNetProfit())).reversed())
                .limit(5).map(this::toAnalysisItem).toList();
        List<PerformanceAnalysisItemDto> losses = records.stream()
                .filter(record -> zero(record.getNetProfit()).signum() < 0)
                .sorted(Comparator.comparing(record -> zero(record.getNetProfit())))
                .limit(5).map(this::toAnalysisItem).toList();
        return PerformanceAnalysisDto.builder()
                .yearMonth(yearMonth.format(MONTH_FORMATTER))
                .asOf(LocalDateTime.now())
                .grossProfit(grossProfit)
                .netProfit(netProfit)
                .totalFees(totalFees)
                .returnRate(rate(netProfit, sumCost(records)))
                .tradeCount(records.size())
                .winningTradeCount(winningCount)
                .losingTradeCount(losingCount)
                .winRate(rate(winningCount, records.size()))
                .topGains(gains)
                .topLosses(losses)
                .availabilityReason(records.isEmpty() ? "该自然月没有已实现交易记录" : null)
                .build();
    }

    /**
     * 查询最近一个存在已实现交易的自然月收益和主要损益来源。
     *
     * @return 最近有交易月份的收益分析；账户无已实现交易时返回明确无数据结果
     */
    public PerformanceAnalysisDto getLatestAnalysisWithTrades() {
        AccountSnapshotEntity account = getLatestAccountSnapshot();
        String brokerCode = account.getBrokerCode();
        String accountId = account.getAccountId();
        return tradeRecordRepository
                .findFirstByBrokerCodeAndAccountIdAndSoldQuantityGreaterThanAndLastSellTimeIsNotNullOrderByLastSellTimeDesc(
                        brokerCode, accountId, 0)
                .map(record -> getAnalysis(YearMonth.from(record.getLastSellTime())))
                .orElseGet(() -> PerformanceAnalysisDto.builder()
                        .yearMonth(null)
                        .asOf(LocalDateTime.now())
                        .grossProfit(BigDecimal.ZERO)
                        .netProfit(BigDecimal.ZERO)
                        .totalFees(BigDecimal.ZERO)
                        .returnRate(BigDecimal.ZERO.setScale(RATE_SCALE))
                        .tradeCount(0)
                        .winningTradeCount(0)
                        .losingTradeCount(0)
                        .winRate(BigDecimal.ZERO.setScale(RATE_SCALE))
                        .topGains(List.of())
                        .topLosses(List.of())
                        .availabilityReason("账户没有已实现交易记录")
                        .build());
    }

    private AccountSnapshotEntity getLatestAccountSnapshot() {
        AccountSnapshotEntity account = accountSnapshotRepository.findFirstByOrderByCapturedAtDesc()
                .orElseThrow(() -> new IllegalStateException("暂无账户资金快照，请等待券商定时同步任务执行"));
        if (account.getBrokerCode() == null || account.getBrokerCode().isBlank()
                || account.getAccountId() == null || account.getAccountId().isBlank()) {
            throw new IllegalStateException("最新账户资金快照缺少券商或账户标识");
        }
        return account;
    }

    private List<PerformanceRankingItemDto> buildRanking(List<TradeRecordEntity> records) {
        return records.stream()
                .sorted(Comparator.comparing(this::safeReturnRate).reversed())
                .map(record -> PerformanceRankingItemDto.builder()
                        .stockCode(record.getStockCode())
                        .stockName(record.getStockName())
                        .transactionAmount(cost(record))
                        .buyAmount(zero(record.getBuyAmount()))
                        .sellAmount(zero(record.getSellAmount()))
                        .tradeQuantity(record.getSoldQuantity())
                        .netProfit(zero(record.getNetProfit()))
                        .returnRate(zero(record.getReturnRate()))
                        .dailyReturnRate(zero(record.getDailyReturnRate()))
                        .buyTime(record.getBuyTime())
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
        BigDecimal cumulativeProfit = BigDecimal.ZERO;
        List<PerformancePeriodItemDto> items = new ArrayList<>();
        for (Map.Entry<String, PeriodAccumulator> entry : grouped.entrySet()) {
            PeriodAccumulator accumulator = entry.getValue();
            cumulativeProfit = cumulativeProfit.add(accumulator.netProfit);
            items.add(PerformancePeriodItemDto.builder()
                    .period(entry.getKey())
                    .netProfit(accumulator.netProfit)
                    .cumulativeProfit(cumulativeProfit)
                    .returnRate(rate(accumulator.netProfit, accumulator.cost))
                    .tradeCount(accumulator.count)
                    .winningTradeCount(accumulator.winningCount)
                    .build());
        }
        return items.stream()
                .sorted(Comparator.comparing(PerformancePeriodItemDto::getPeriod).reversed())
                .limit(12)
                .toList();
    }

    private List<PerformanceDailyReturnItemDto> buildDailyItems(List<TradeRecordEntity> records) {
        return records.stream()
                .filter(record -> record.getLastSellTime() != null)
                .sorted(Comparator.comparing(TradeRecordEntity::getLastSellTime).reversed())
                .limit(12)
                .map(record -> PerformanceDailyReturnItemDto.builder()
                        .period(record.getLastSellTime().toLocalDate().format(DAY_FORMATTER))
                        .stockCode(record.getStockCode())
                        .stockName(record.getStockName())
                        .buyAmount(zero(record.getBuyAmount()))
                        .sellAmount(zero(record.getSellAmount()))
                        .netProfit(zero(record.getNetProfit()))
                        .transactionAmount(cost(record))
                        .returnRate(safeReturnRate(record))
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

    private BigDecimal fees(TradeRecordEntity record) {
        return zero(record.getAllocatedBuyFee()).add(zero(record.getSellFee()));
    }

    private PerformanceAnalysisItemDto toAnalysisItem(TradeRecordEntity record) {
        return PerformanceAnalysisItemDto.builder()
                .stockCode(record.getStockCode())
                .stockName(record.getStockName())
                .grossProfit(zero(record.getGrossProfit()))
                .netProfit(zero(record.getNetProfit()))
                .fees(fees(record))
                .returnRate(safeReturnRate(record))
                .sellTime(record.getLastSellTime())
                .build();
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
// AI_GENERATE_END-------------------
