package com.stock.strategyAnalysis.selector;

import com.stock.strategyAnalysis.config.StrategyConfigService;
import com.stock.strategyAnalysis.dto.SelectionResult;
import com.stock.strategyAnalysis.dto.StockRankingDto;
import com.stock.strategyAnalysis.entity.StockRanking;
import com.stock.strategyAnalysis.entity.StrategyConfig;
import com.stock.strategyAnalysis.repository.RankingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 股票选择器
 * 执行综合选股策略，基于双因子模型进行股票评分和排名
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StockSelector {

    private final ScoreCalculator scoreCalculator;
    private final StrategyConfigService configService;
    private final RankingRepository rankingRepository;

    /**
     * 执行选股，返回 Top N 股票
     */
    public SelectionResult selectTopN(int n) {
        log.info("开始选股, 目标数量: {}", n);
        long startTime = System.currentTimeMillis();
        
        try {
            StrategyConfig config = configService.getCurrentConfig();
            List<String> stockCodes = getTradableStocks();
            
            if (stockCodes.isEmpty()) {
                log.warn("无可交易股票");
                return SelectionResult.builder().success(false).errorMessage("无可交易股票").build();
            }
            
            Map<String, Double> lstmPredictions = getLstmPredictions(stockCodes);
            Map<String, Double> sentimentScores = getSentimentScores(stockCodes);
            Map<String, Double> totalScores = scoreCalculator.calculateTotalScores(
                    lstmPredictions, sentimentScores, config);
            
            List<StockRankingDto> rankings = rankAndSelect(totalScores, lstmPredictions, 
                    sentimentScores, n, config);
            
            saveRankingResults(rankings);
            
            long costTime = System.currentTimeMillis() - startTime;
            log.info("选股完成, 共 {} 只股票参与, 选出 {} 只, 耗时 {}ms", 
                    stockCodes.size(), rankings.size(), costTime);
            
            return SelectionResult.builder()
                    .executeTime(LocalDateTime.now())
                    .totalStocks(stockCodes.size())
                    .topN(rankings)
                    .costTimeMs(costTime)
                    .success(true)
                    .build();
                    
        } catch (Exception e) {
            log.error("选股失败", e);
            return SelectionResult.builder().success(false).errorMessage(e.getMessage()).build();
        }
    }

    /**
     * 获取单只股票的排名
     */
    public StockRankingDto getStockRanking(String stockCode) {
        StockRanking ranking = rankingRepository.findFirstByStockCodeOrderByCalculateTimeDesc(stockCode);
        return ranking != null ? convertToDto(ranking) : null;
    }

    /**
     * 获取最新的所有排名
     */
    public List<StockRankingDto> getAllRankings() {
        LocalDate today = LocalDate.now();
        List<StockRanking> rankings = rankingRepository.findTop10ByCalculateTimeOrderByRankAsc(today);
        return rankings.stream().map(this::convertToDto).collect(Collectors.toList());
    }

    /**
     * 获取可交易股票列表
     * TODO: 从 data-collector 模块获取
     */
    private List<String> getTradableStocks() {
        return Arrays.asList("600519", "000858", "601318", "600036", "000001",
                "601166", "600276", "000333", "601398", "600030");
    }

    /**
     * 获取 LSTM 预测得分
     * TODO: 从 model-service 模块获取
     */
    private Map<String, Double> getLstmPredictions(List<String> stockCodes) {
        Map<String, Double> predictions = new HashMap<>();
        Random random = new Random();
        for (String code : stockCodes) {
            predictions.put(code, 0.4 + random.nextDouble() * 0.4);
        }
        return predictions;
    }

    /**
     * 获取情感得分
     * TODO: 从 model-service 模块获取
     */
    private Map<String, Double> getSentimentScores(List<String> stockCodes) {
        Map<String, Double> sentiments = new HashMap<>();
        Random random = new Random();
        for (String code : stockCodes) {
            sentiments.put(code, -0.5 + random.nextDouble());
        }
        return sentiments;
    }

    /**
     * 排序并筛选 Top N
     */
    private List<StockRankingDto> rankAndSelect(
            Map<String, Double> totalScores,
            Map<String, Double> lstmPredictions,
            Map<String, Double> sentimentScores,
            int n,
            StrategyConfig config) {
        
        List<Map.Entry<String, Double>> sortedEntries = totalScores.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .collect(Collectors.toList());
        
        List<StockRankingDto> rankings = new ArrayList<>();
        int rank = 1;
        
        for (Map.Entry<String, Double> entry : sortedEntries) {
            if (rank > n) break;
            
            String stockCode = entry.getKey();
            double totalScore = entry.getValue();
            
            if (!scoreCalculator.meetsMinScoreRequirement(totalScore, config)) continue;
            
            rankings.add(StockRankingDto.builder()
                    .stockCode(stockCode)
                    .stockName(stockCode)
                    .lstmScore(lstmPredictions.getOrDefault(stockCode, 0.5))
                    .sentimentScore(sentimentScores.getOrDefault(stockCode, 0.0))
                    .totalScore(totalScore)
                    .rank(rank)
                    .reason(generateReason(totalScore, rank))
                    .build());
            rank++;
        }
        return rankings;
    }

    private String generateReason(double totalScore, int rank) {
        if (totalScore >= 0.7) return String.format("综合得分%.2f, 排名第%d, 强烈推荐买入", totalScore, rank);
        if (totalScore >= 0.5) return String.format("综合得分%.2f, 排名第%d, 推荐买入", totalScore, rank);
        return String.format("综合得分%.2f, 排名第%d, 建议关注", totalScore, rank);
    }

    private void saveRankingResults(List<StockRankingDto> rankings) {
        LocalDateTime now = LocalDateTime.now();
        List<StockRanking> entities = rankings.stream()
                .map(dto -> StockRanking.builder()
                        .stockCode(dto.getStockCode())
                        .stockName(dto.getStockName())
                        .lstmScore(dto.getLstmScore())
                        .sentimentScore(dto.getSentimentScore())
                        .totalScore(dto.getTotalScore())
                        .rank(dto.getRank())
                        .calculateTime(now)
                        .reason(dto.getReason())
                        .build())
                .collect(Collectors.toList());
        rankingRepository.saveAll(entities);
    }

    private StockRankingDto convertToDto(StockRanking ranking) {
        return StockRankingDto.builder()
                .stockCode(ranking.getStockCode())
                .stockName(ranking.getStockName())
                .lstmScore(ranking.getLstmScore())
                .sentimentScore(ranking.getSentimentScore())
                .totalScore(ranking.getTotalScore())
                .rank(ranking.getRank())
                .reason(ranking.getReason())
                .build();
    }
}
