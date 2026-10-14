// AI_GENERATE_START ---------
package com.stock.tradingExecutor.service;

import com.stock.dataCollector.service.TradingCalendarService;
import com.stock.strategyAnalysis.domain.dto.StockRankingDto;
import com.stock.strategyAnalysis.engine.StockSelector;
import com.stock.tradingExecutor.config.RiskConfig;
import com.stock.tradingExecutor.domain.dto.RealTradingStatusDto;
import com.stock.tradingExecutor.domain.dto.TradeExecutionBatchResponseDto;
import com.stock.tradingExecutor.domain.dto.TradeExecutionResponseDto;
import com.stock.tradingExecutor.domain.dto.TradingCandidateDto;
import com.stock.tradingExecutor.domain.dto.TradingCandidateListResponseDto;
import com.stock.tradingExecutor.domain.entity.Position;
import com.stock.tradingExecutor.domain.vo.BrokerOrderSnapshot;
import com.stock.tradingExecutor.domain.vo.OrderResult;
import com.stock.tradingExecutor.execution.BrokerAdapter;
import com.stock.tradingExecutor.execution.TradeExecutor;
import com.stock.tradingExecutor.execution.TradingProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 真实交易业务入口。
 * 只连接真实模型候选、券商事实、风控和真实委托。
 *
 * @author mwangli
 * @since 2026-09-25
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RealTradingService {

    private final StockSelector stockSelector;
    private final BrokerAdapter brokerAdapter;
    private final TradeExecutor tradeExecutor;
    private final TradingProperties tradingProperties;
    private final RiskConfig riskConfig;
    private final TradingCalendarService tradingCalendarService;
    private final AutomaticTradeExecutionBatchService executionBatchService;

    /**
     * 查询真实交易运行状态。
     *
     * @return 当前交易模式、总门禁和券商会话状态
     */
    public RealTradingStatusDto getStatus() {
        boolean authenticated = brokerAdapter.isAuthenticated();
        return RealTradingStatusDto.builder()
                .realWriteEnabled(tradingProperties.isLiveWriteAllowed())
                .brokerAuthenticated(authenticated)
                .automaticExecutionEnabled(authenticated
                        && tradingProperties.isLiveWriteAllowed())
                .build();
    }

    /**
     * 查询当日模型候选，并标记真实账户是否已经持仓。
     *
     * @return 当日候选列表
     */
    public TradingCandidateListResponseDto getCandidates() {
        List<StockRankingDto> rankings = loadTodayCandidates();
        boolean positionSnapshotAvailable = brokerAdapter.isAuthenticated();
        Set<String> heldCodes = positionSnapshotAvailable
                ? brokerAdapter.getPositions().stream()
                        .map(Position::getStockCode)
                        .collect(Collectors.toSet())
                : Set.of();
        List<TradingCandidateDto> items = rankings.stream()
                .map(item -> toCandidate(item, heldCodes.contains(item.getStockCode())))
                .toList();
        StockRankingDto first = rankings.get(0);
        return TradingCandidateListResponseDto.builder()
                .candidateBatchId(first.getCandidateBatchId())
                .predictionBatchId(first.getPredictionBatchId())
                .modelVersion(first.getModelVersion())
                .sentimentModelVersion(first.getSentimentModelVersion())
                .strategyConfigVersion(first.getStrategyConfigVersion())
                .baseTradeDate(first.getBaseTradeDate())
                .targetTradeDate(first.getTargetTradeDate())
                .informationCutoffAt(first.getInformationCutoffAt())
                .positionSnapshotAvailable(positionSnapshotAvailable)
                .items(items)
                .build();
    }

    /**
     * 在无人值守模式下执行当日排名靠前且未持仓的候选。
     *
     * @return 本批次真实委托结果
     */
    public TradeExecutionBatchResponseDto executeAutomaticBuys() {
        requireAutomaticExecution();
        LocalDate today = LocalDate.now();
        tradingCalendarService.requireTradingDay(today);
        List<StockRankingDto> rankings = loadTodayCandidates();
        String executionBatchId = executionBatchService
                .begin("BUY", today, rankings.get(0).getCandidateBatchId())
                .getBatchId();
        try {
            List<Position> positions = brokerAdapter.getPositions();
            Set<String> heldCodes = positions.stream().map(Position::getStockCode).collect(Collectors.toSet());
            List<TradeExecutionResponseDto> results = new ArrayList<>();

            for (StockRankingDto candidate : rankings) {
                if (results.size() >= tradingProperties.getAutoCandidateLimit()) {
                    break;
                }
                if (heldCodes.contains(candidate.getStockCode())) {
                    continue;
                }
                ensureNoDuplicateOrder(candidate.getStockCode(), "BUY");
                OrderResult result = tradeExecutor.executeBuy(
                        candidate.getStockCode(), tradingProperties.getAutoBuyAmount());
                results.add(toExecution(result));
                if (result.isSuccess()) {
                    heldCodes.add(candidate.getStockCode());
                }
            }
            TradeExecutionBatchResponseDto response = buildBatch(
                    executionBatchId, results, "无人值守买入检查完成");
            executionBatchService.complete(executionBatchId, results.size(),
                    response.getSuccessCount(), response.getMessage());
            return response;
        } catch (RuntimeException exception) {
            markExecutionBatchFailed(executionBatchId, exception);
            throw exception;
        }
    }

    /**
     * 检查真实持仓的止损、止盈和 T+1 尾盘退出条件，并执行可卖数量。
     *
     * @return 本批次真实委托结果
     */
    public TradeExecutionBatchResponseDto executeAutomaticExits() {
        requireAutomaticExecution();
        LocalDate today = LocalDate.now();
        tradingCalendarService.requireTradingDay(today);
        String executionBatchId = executionBatchService.begin("EXIT", today, null).getBatchId();
        try {
            List<TradeExecutionResponseDto> results = new ArrayList<>();
            boolean forceExit = !LocalTime.now().isBefore(tradingProperties.getForceExitTime());

            for (Position position : brokerAdapter.getPositions()) {
                Integer availableQuantity = position.getAvailableQuantity();
                if (availableQuantity == null || availableQuantity <= 0) {
                    continue;
                }
                BigDecimal profitLossPercent = position.getProfitLossPercent();
                boolean stopLoss = profitLossPercent != null
                        && profitLossPercent.compareTo(BigDecimal.valueOf(-riskConfig.getSingleStockStopLoss())) <= 0;
                boolean takeProfit = profitLossPercent != null
                        && profitLossPercent.compareTo(BigDecimal.valueOf(tradingProperties.getTakeProfitPercent())) >= 0;
                if (!stopLoss && !takeProfit && !forceExit) {
                    continue;
                }
                ensureNoDuplicateOrder(position.getStockCode(), "SELL");
                OrderResult result = tradeExecutor.executeSell(
                        position.getStockCode(), BigDecimal.valueOf(availableQuantity));
                results.add(toExecution(result));
            }
            TradeExecutionBatchResponseDto response = buildBatch(
                    executionBatchId, results, "无人值守 T+1 退出检查完成");
            executionBatchService.complete(executionBatchId, results.size(),
                    response.getSuccessCount(), response.getMessage());
            return response;
        } catch (RuntimeException exception) {
            markExecutionBatchFailed(executionBatchId, exception);
            throw exception;
        }
    }

    private void requireBrokerSession() {
        brokerAdapter.ensureAuthenticated();
    }

    private void requireWriteGate() {
        requireBrokerSession();
        if (!tradingProperties.isLiveWriteAllowed()) {
            throw new IllegalStateException("真实交易写入总门禁未开启");
        }
    }

    private void requireAutomaticExecution() {
        requireWriteGate();
    }

    private List<StockRankingDto> loadTodayCandidates() {
        LocalDate today = LocalDate.now();
        List<StockRankingDto> rankings = stockSelector.getExecutableRankings(today);
        if (rankings.isEmpty()) {
            throw new IllegalStateException("当前交易日没有元数据完整的可执行候选: " + today);
        }
        StockRankingDto first = rankings.get(0);
        boolean inconsistent = rankings.stream().anyMatch(item ->
                !first.getCandidateBatchId().equals(item.getCandidateBatchId())
                        || !first.getPredictionBatchId().equals(item.getPredictionBatchId())
                        || !today.equals(item.getTargetTradeDate()));
        if (inconsistent) {
            throw new IllegalStateException("候选批次元数据不一致，禁止进入真实交易");
        }
        return rankings;
    }

    private void ensureNoDuplicateOrder(String stockCode, String direction) {
        boolean duplicated = brokerAdapter.getTodayOrderSnapshots().stream()
                .filter(order -> stockCode.equals(order.getStockCode()))
                .filter(order -> direction.equalsIgnoreCase(order.getDirection()))
                .map(BrokerOrderSnapshot::getStatus)
                .anyMatch(status -> status == null || !status.isFinal() || status.isSuccess());
        if (duplicated) {
            throw new IllegalStateException("当日已存在同方向委托或成交，禁止重复下单");
        }
    }

    private void markExecutionBatchFailed(String executionBatchId, RuntimeException exception) {
        try {
            executionBatchService.fail(executionBatchId, exception.getMessage());
        } catch (RuntimeException persistenceException) {
            log.error("自动交易执行批次失败状态保存异常, executionBatchId={}",
                    executionBatchId, persistenceException);
        }
    }

    private TradingCandidateDto toCandidate(StockRankingDto item, boolean held) {
        return TradingCandidateDto.builder()
                .stockCode(item.getStockCode())
                .stockName(item.getStockName())
                .lstmScore(item.getLstmScore())
                .sentimentScore(item.getSentimentScore())
                .totalScore(item.getTotalScore())
                .rank(item.getRank())
                .reason(item.getReason())
                .held(held)
                .build();
    }

    private TradeExecutionResponseDto toExecution(OrderResult result) {
        return TradeExecutionResponseDto.builder()
                .success(result.isSuccess())
                .orderId(result.getOrderId())
                .stockCode(result.getStockCode())
                .direction(result.getDirection())
                .price(result.getPrice())
                .quantity(result.getQuantity())
                .status(result.getStatus())
                .message(result.getMessage())
                .submitTime(result.getSubmitTime())
                .fillTime(result.getFillTime())
                .build();
    }

    private TradeExecutionBatchResponseDto buildBatch(
            String executionBatchId, List<TradeExecutionResponseDto> results, String message) {
        int successCount = (int) results.stream().filter(TradeExecutionResponseDto::isSuccess).count();
        return TradeExecutionBatchResponseDto.builder()
                .executionBatchId(executionBatchId)
                .items(results)
                .successCount(successCount)
                .message(message)
                .build();
    }
}
// AI_GENERATE_END ---------
