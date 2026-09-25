// AI_GENERATE_START -
package com.stock.tradingExecutor.service;

import com.stock.strategyAnalysis.domain.dto.StockRankingDto;
import com.stock.strategyAnalysis.engine.StockSelector;
import com.stock.tradingExecutor.config.RiskConfig;
import com.stock.tradingExecutor.domain.dto.RealBuyRequestDto;
import com.stock.tradingExecutor.domain.dto.RealSellRequestDto;
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
import com.stock.tradingExecutor.execution.TradingMode;
import com.stock.tradingExecutor.execution.TradingProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 真实交易业务入口。
 * 只连接真实模型候选、券商事实、风控和真实委托，不提供模拟或降级数据。
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

    /**
     * 查询真实交易运行状态。
     *
     * @return 当前交易模式、总门禁和券商会话状态
     */
    public RealTradingStatusDto getStatus() {
        boolean authenticated = brokerAdapter.isAuthenticated();
        return RealTradingStatusDto.builder()
                .mode(tradingProperties.getMode())
                .realWriteEnabled(tradingProperties.isLiveWriteAllowed())
                .brokerAuthenticated(authenticated)
                .automaticExecutionEnabled(authenticated
                        && tradingProperties.isLiveWriteAllowed()
                        && tradingProperties.getMode().isAutomatic())
                .build();
    }

    /**
     * 查询当日模型候选，并标记真实账户是否已经持仓。
     *
     * @return 当日候选列表
     */
    public TradingCandidateListResponseDto getCandidates() {
        requireBrokerSession();
        Set<String> heldCodes = brokerAdapter.getPositions().stream()
                .map(Position::getStockCode)
                .collect(Collectors.toSet());
        List<TradingCandidateDto> items = stockSelector.getAllRankings().stream()
                .map(item -> toCandidate(item, heldCodes.contains(item.getStockCode())))
                .toList();
        return TradingCandidateListResponseDto.builder().items(items).build();
    }

    /**
     * 人工确认买入当日模型候选。
     *
     * @param request 买入请求
     * @return 真实委托执行结果
     */
    public TradeExecutionResponseDto executeManualBuy(RealBuyRequestDto request) {
        validateBuyRequest(request);
        if (tradingProperties.getMode() != TradingMode.LIVE_MANUAL) {
            throw new IllegalStateException("当前不是人工确认交易模式");
        }
        requireWriteGate();
        ensureCurrentCandidate(request.getStockCode());
        ensureNoDuplicateOrder(request.getStockCode(), "BUY");
        OrderResult result = request.isMonitorPrice()
                ? tradeExecutor.executeBuyWithMonitor(request.getStockCode(), request.getAmount())
                : tradeExecutor.executeBuy(request.getStockCode(), request.getAmount());
        return toExecution(result);
    }

    /**
     * 人工卖出指定的可用持仓数量。
     * 该入口保留给人工止损和紧急退出，自动模式下也允许人工主动调用。
     *
     * @param request 卖出请求
     * @return 真实委托执行结果
     */
    public TradeExecutionResponseDto executeManualSell(RealSellRequestDto request) {
        validateSellRequest(request);
        requireWriteGate();
        ensureNoDuplicateOrder(request.getStockCode(), "SELL");
        return toExecution(tradeExecutor.executeSell(request.getStockCode(), request.getQuantity()));
    }

    /**
     * 在无人值守模式下执行当日排名靠前且未持仓的候选。
     *
     * @return 本批次真实委托结果
     */
    public TradeExecutionBatchResponseDto executeAutomaticBuys() {
        requireAutomaticExecution();
        List<Position> positions = brokerAdapter.getPositions();
        Set<String> heldCodes = positions.stream().map(Position::getStockCode).collect(Collectors.toSet());
        List<TradeExecutionResponseDto> results = new ArrayList<>();

        for (StockRankingDto candidate : stockSelector.getAllRankings()) {
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
        return buildBatch(results, "无人值守买入检查完成");
    }

    /**
     * 检查真实持仓的止损、止盈和 T+1 尾盘退出条件，并执行可卖数量。
     *
     * @return 本批次真实委托结果
     */
    public TradeExecutionBatchResponseDto executeAutomaticExits() {
        requireAutomaticExecution();
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
        return buildBatch(results, "无人值守 T+1 退出检查完成");
    }

    private void requireBrokerSession() {
        if (!brokerAdapter.isAuthenticated()) {
            throw new IllegalStateException("券商会话无效，请先完成真实账户登录");
        }
    }

    private void requireWriteGate() {
        requireBrokerSession();
        if (!tradingProperties.isLiveWriteAllowed()) {
            throw new IllegalStateException("真实交易写入总门禁未开启");
        }
    }

    private void requireAutomaticExecution() {
        requireWriteGate();
        if (!tradingProperties.getMode().isAutomatic()) {
            throw new IllegalStateException("当前不是无人值守交易模式");
        }
    }

    private void validateBuyRequest(RealBuyRequestDto request) {
        if (request == null || request.getStockCode() == null || request.getStockCode().isBlank()) {
            throw new IllegalArgumentException("股票代码不能为空");
        }
        if (request.getAmount() == null || request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("买入金额必须大于零");
        }
    }

    private void validateSellRequest(RealSellRequestDto request) {
        if (request == null || request.getStockCode() == null || request.getStockCode().isBlank()) {
            throw new IllegalArgumentException("股票代码不能为空");
        }
        if (request.getQuantity() == null || request.getQuantity().compareTo(BigDecimal.ZERO) <= 0
                || request.getQuantity().stripTrailingZeros().scale() > 0) {
            throw new IllegalArgumentException("卖出数量必须是正整数");
        }
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

    private void ensureCurrentCandidate(String stockCode) {
        boolean matched = stockSelector.getAllRankings().stream()
                .anyMatch(item -> stockCode.equals(item.getStockCode()));
        if (!matched) {
            throw new IllegalArgumentException("该股票不在当日真实模型候选中");
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

    private TradeExecutionBatchResponseDto buildBatch(List<TradeExecutionResponseDto> results, String message) {
        int successCount = (int) results.stream().filter(TradeExecutionResponseDto::isSuccess).count();
        return TradeExecutionBatchResponseDto.builder()
                .items(results)
                .successCount(successCount)
                .message(message)
                .build();
    }
}
// AI_GENERATE_END -
