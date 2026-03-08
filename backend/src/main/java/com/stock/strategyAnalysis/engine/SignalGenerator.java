package com.stock.strategyAnalysis.engine;

import com.stock.strategyAnalysis.config.StrategyConfigService;
import com.stock.strategyAnalysis.domain.vo.SignalGenerationResult;
import com.stock.strategyAnalysis.domain.dto.StockRankingDto;
import com.stock.strategyAnalysis.domain.entity.TradingSignal;
import com.stock.strategyAnalysis.domain.dto.TradingSignalDto;
import com.stock.strategyAnalysis.domain.entity.Signal;
import com.stock.strategyAnalysis.domain.entity.StrategyConfig;
import com.stock.strategyAnalysis.persistence.SignalRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 信号生成器
 * 根据选股结果生成买入和卖出信号
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SignalGenerator {

    private final StockSelector stockSelector;
    private final ScoreCalculator scoreCalculator;
    private final StrategyConfigService configService;
    private final SignalRepository signalRepository;

    /**
     * 生成交易信号
     */
    public SignalGenerationResult generateSignals() {
        log.info("开始生成交易信号");
        long startTime = System.currentTimeMillis();

        try {
            StrategyConfig config = configService.getCurrentConfig();

            // 获取选股结果
            List<StockRankingDto> rankings = stockSelector.getAllRankings();

            if (rankings.isEmpty()) {
                log.warn("无选股结果，无法生成信号");
                return SignalGenerationResult.builder()
                        .success(false)
                        .errorMessage("无选股结果")
                        .build();
            }

            // 生成买入信号
            List<TradingSignalDto> buySignals = generateBuySignals(rankings, config);

            // 生成卖出信号（基于排名）
            List<TradingSignalDto> sellSignals = generateSellSignals(rankings, config);

            // 保存信号
            saveSignals(buySignals, sellSignals);

            long costTime = System.currentTimeMillis() - startTime;
            log.info("信号生成完成, 买入: {}, 卖出: {}, 耗时: {}ms",
                    buySignals.size(), sellSignals.size(), costTime);

            return SignalGenerationResult.builder()
                    .generateTime(LocalDateTime.now())
                    .buySignals(buySignals)
                    .sellSignals(sellSignals)
                    .buyCount(buySignals.size())
                    .sellCount(sellSignals.size())
                    .costTimeMs(costTime)
                    .success(true)
                    .build();

        } catch (Exception e) {
            log.error("信号生成失败", e);
            return SignalGenerationResult.builder()
                    .success(false)
                    .errorMessage(e.getMessage())
                    .build();
        }
    }

    /**
     * 生成买入信号
     * 规则：综合排名前N且无持仓 → 买入信号
     */
    private List<TradingSignalDto> generateBuySignals(List<StockRankingDto> rankings, StrategyConfig config) {
        List<TradingSignalDto> signals = new ArrayList<>();

        int topN = Math.min(config.getTopN(), rankings.size());

        for (int i = 0; i < topN; i++) {
            StockRankingDto ranking = rankings.get(i);

            // 检查是否满足最低得分要求
            if (ranking.getTotalScore() < config.getMinScore()) {
                continue;
            }

            // TODO: 检查是否已有持仓（需要从执行模块获取持仓信息）

            TradingSignalDto signal = TradingSignalDto.builder()
                    .signalId(UUID.randomUUID().toString())
                    .stockCode(ranking.getStockCode())
                    .stockName(ranking.getStockName())
                    .signalType(Signal.BUY)
                    .strength(scoreCalculator.calculateBuySignalStrength(ranking.getRank()))
                    .confidence(ranking.getTotalScore())
                    .reason(ranking.getReason())
                    .generateTime(LocalDateTime.now())
                    .build();

            signals.add(signal);
        }

        return signals;
    }

    /**
     * 生成卖出信号（基于排名）
     * 规则：持仓股跌出前M → 卖出信号
     */
    private List<TradingSignalDto> generateSellSignals(List<StockRankingDto> rankings, StrategyConfig config) {
        List<TradingSignalDto> signals = new ArrayList<>();

        // TODO: 从执行模块获取当前持仓列表

        // 临时逻辑：如果排名在10名之后，生成卖出信号
        int thresholdRank = 10;

        for (StockRankingDto ranking : rankings) {
            if (ranking.getRank() > thresholdRank) {
                TradingSignalDto signal = TradingSignalDto.builder()
                        .signalId(UUID.randomUUID().toString())
                        .stockCode(ranking.getStockCode())
                        .stockName(ranking.getStockName())
                        .signalType(Signal.SELL)
                        .strength(scoreCalculator.calculateSellSignalStrength(ranking.getRank(), thresholdRank))
                        .confidence(0.7)
                        .reason(String.format("排名跌出前%d, 当前排名%d", thresholdRank, ranking.getRank()))
                        .generateTime(LocalDateTime.now())
                        .build();

                signals.add(signal);
            }
        }

        return signals;
    }

    /**
     * 保存信号
     */
    private void saveSignals(List<TradingSignalDto> buySignals, List<TradingSignalDto> sellSignals) {
        List<TradingSignal> entities = new ArrayList<>();

        entities.addAll(buySignals.stream()
                .map(this::convertToEntity)
                .collect(Collectors.toList()));

        entities.addAll(sellSignals.stream()
                .map(this::convertToEntity)
                .collect(Collectors.toList()));

        signalRepository.saveAll(entities);
        log.info("保存交易信号 {} 条", entities.size());
    }

    /**
     * 转换为实体
     */
    private TradingSignal convertToEntity(TradingSignalDto dto) {
        return TradingSignal.builder()
                .signalId(dto.getSignalId())
                .stockCode(dto.getStockCode())
                .stockName(dto.getStockName())
                .signalType(dto.getSignalType())
                .strength(dto.getStrength())
                .confidence(dto.getConfidence())
                .reason(dto.getReason())
                .generateTime(dto.getGenerateTime())
                .executed(false)
                .build();
    }
}
