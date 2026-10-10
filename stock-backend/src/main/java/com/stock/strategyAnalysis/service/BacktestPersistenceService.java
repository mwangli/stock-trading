// AI_GENERATE_START ---
package com.stock.strategyAnalysis.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stock.strategyAnalysis.domain.dto.BacktestResultDto;
import com.stock.strategyAnalysis.domain.dto.BacktestTradeDto;
import com.stock.strategyAnalysis.domain.dto.DailyBaselineBacktestRequestDto;
import com.stock.strategyAnalysis.domain.entity.BacktestRunEntity;
import com.stock.strategyAnalysis.domain.entity.BacktestTradeEntity;
import com.stock.strategyAnalysis.persistence.BacktestRunRepository;
import com.stock.strategyAnalysis.persistence.BacktestTradeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 回测运行和逐笔交易持久化服务。
 *
 * @author mwangli
 * @since 2026-09-25
 */
@Service
@RequiredArgsConstructor
public class BacktestPersistenceService {

    private final BacktestRunRepository backtestRunRepository;
    private final BacktestTradeRepository backtestTradeRepository;
    private final ObjectMapper objectMapper;

    /**
     * 原子保存已完成回测的汇总和逐笔明细。
     *
     * @param request 原始回测请求
     * @param result 回测结果
     */
    @Transactional
    public void saveCompleted(DailyBaselineBacktestRequestDto request, BacktestResultDto result) {
        LocalDateTime now = LocalDateTime.now();
        BacktestRunEntity run = BacktestRunEntity.builder()
                .resultId(result.getResultId())
                .baselineType(result.getBaselineType())
                .startDate(result.getStartDate())
                .endDate(result.getEndDate())
                .status("COMPLETED")
                .degraded(result.isDegraded())
                .dataVersion(result.getDataVersion())
                .stockPoolVersion(result.getStockPoolVersion())
                .featureVersion(result.getFeatureVersion())
                .feeVersion(result.getFeeVersion())
                .codeVersion(result.getCodeVersion())
                .totalReturn(decimal(result.getTotalReturn()))
                .annualizedReturn(decimal(result.getAnnualizedReturn()))
                .maxDrawdown(decimal(result.getMaxDrawdown()))
                .netReturn1000(decimal(result.getNetReturn1000()))
                .netReturn5000(decimal(result.getNetReturn5000()))
                .netReturn10000(decimal(result.getNetReturn10000()))
                .totalTrades(result.getTotalTrades())
                .winTrades(result.getWinTrades())
                .winRate(decimal(result.getWinRate()))
                .avgProfit(decimal(result.getAvgProfit()))
                .avgLoss(decimal(result.getAvgLoss()))
                .blockedTrades(result.getBlockedTrades())
                .skippedTrades(result.getSkippedTrades())
                .costTimeMs(result.getCostTimeMs())
                .requestJson(toJson(request))
                .limitationsJson(toJson(result.getLimitations()))
                .calculatedAt(now)
                .createdAt(now)
                .build();
        backtestRunRepository.save(run);

        List<BacktestTradeEntity> trades = new ArrayList<>();
        for (int index = 0; index < result.getTrades().size(); index++) {
            BacktestTradeDto trade = result.getTrades().get(index);
            trades.add(toEntity(result.getResultId(), index + 1, trade, now));
        }
        backtestTradeRepository.saveAll(trades);
    }

    /**
     * 查询已持久化的回测结果和逐笔明细。
     *
     * @param resultId 回测结果标识
     * @return 完整回测结果
     */
    @Transactional(readOnly = true)
    public BacktestResultDto findResult(String resultId) {
        BacktestRunEntity run = backtestRunRepository.findByResultId(resultId)
                .orElseThrow(() -> new IllegalArgumentException("回测结果不存在: " + resultId));
        List<BacktestTradeDto> trades = backtestTradeRepository
                .findByResultIdOrderBySequenceNoAsc(resultId).stream()
                .map(this::toDto)
                .toList();
        return BacktestResultDto.builder()
                .resultId(run.getResultId())
                .startDate(run.getStartDate())
                .endDate(run.getEndDate())
                .totalReturn(run.getTotalReturn().doubleValue())
                .annualizedReturn(run.getAnnualizedReturn().doubleValue())
                .maxDrawdown(run.getMaxDrawdown().doubleValue())
                .totalTrades(run.getTotalTrades())
                .winTrades(run.getWinTrades())
                .winRate(run.getWinRate().doubleValue())
                .avgProfit(run.getAvgProfit().doubleValue())
                .avgLoss(run.getAvgLoss().doubleValue())
                .avgHighCaptureRate(0D)
                .calculateTime(run.getCalculatedAt().toString())
                .costTimeMs(run.getCostTimeMs())
                .baselineType(run.getBaselineType())
                .dataVersion(run.getDataVersion())
                .stockPoolVersion(run.getStockPoolVersion())
                .featureVersion(run.getFeatureVersion())
                .feeVersion(run.getFeeVersion())
                .codeVersion(run.getCodeVersion())
                .netReturn1000(run.getNetReturn1000().doubleValue())
                .netReturn5000(run.getNetReturn5000().doubleValue())
                .netReturn10000(run.getNetReturn10000().doubleValue())
                .blockedTrades(run.getBlockedTrades())
                .skippedTrades(run.getSkippedTrades())
                .degraded(run.isDegraded())
                .limitations(fromJsonList(run.getLimitationsJson()))
                .trades(trades)
                .build();
    }

    private BacktestTradeEntity toEntity(String resultId, int sequenceNo,
                                         BacktestTradeDto trade, LocalDateTime createdAt) {
        return BacktestTradeEntity.builder()
                .resultId(resultId)
                .sequenceNo(sequenceNo)
                .stockCode(trade.getStockCode())
                .signalDate(trade.getSignalDate())
                .entryDate(trade.getEntryDate())
                .plannedExitDate(trade.getPlannedExitDate())
                .actualExitDate(trade.getActualExitDate())
                .rawEntryPrice(trade.getRawEntryPrice())
                .rawExitPrice(trade.getRawExitPrice())
                .executedEntryPrice(trade.getExecutedEntryPrice())
                .executedExitPrice(trade.getExecutedExitPrice())
                .quantity(trade.getQuantity())
                .quantity1000(trade.getQuantity1000())
                .quantity10000(trade.getQuantity10000())
                .grossReturn(zeroIfNull(trade.getGrossReturn()))
                .totalFee(zeroIfNull(trade.getTotalFee()))
                .netProfit(zeroIfNull(trade.getNetProfit()))
                .netProfit1000(zeroIfNull(trade.getNetProfit1000()))
                .netProfit10000(zeroIfNull(trade.getNetProfit10000()))
                .status(trade.getStatus())
                .reason(trade.getReason())
                .createdAt(createdAt)
                .build();
    }

    private BacktestTradeDto toDto(BacktestTradeEntity trade) {
        return BacktestTradeDto.builder()
                .stockCode(trade.getStockCode())
                .signalDate(trade.getSignalDate())
                .entryDate(trade.getEntryDate())
                .plannedExitDate(trade.getPlannedExitDate())
                .actualExitDate(trade.getActualExitDate())
                .rawEntryPrice(trade.getRawEntryPrice())
                .rawExitPrice(trade.getRawExitPrice())
                .executedEntryPrice(trade.getExecutedEntryPrice())
                .executedExitPrice(trade.getExecutedExitPrice())
                .quantity(trade.getQuantity())
                .quantity1000(trade.getQuantity1000())
                .quantity10000(trade.getQuantity10000())
                .grossReturn(trade.getGrossReturn())
                .totalFee(trade.getTotalFee())
                .netProfit(trade.getNetProfit())
                .netProfit1000(trade.getNetProfit1000())
                .netProfit10000(trade.getNetProfit10000())
                .status(trade.getStatus())
                .reason(trade.getReason())
                .build();
    }

    private BigDecimal decimal(double value) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException("回测指标包含非有限数值");
        }
        return BigDecimal.valueOf(value);
    }

    private BigDecimal zeroIfNull(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("回测追溯信息序列化失败", exception);
        }
    }

    private List<String> fromJsonList(String value) {
        try {
            return objectMapper.readValue(value, new TypeReference<>() { });
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("回测限制信息反序列化失败", exception);
        }
    }
}
// AI_GENERATE_END ---
