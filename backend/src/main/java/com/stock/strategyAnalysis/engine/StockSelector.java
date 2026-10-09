// AI_GENERATE_START ---------
package com.stock.strategyAnalysis.engine;

import com.stock.dataCollector.domain.entity.StockNews;
import com.stock.dataCollector.persistence.NewsRepository;
import com.stock.modelService.domain.dto.LstmPredictionResultDto;
import com.stock.modelService.domain.vo.SentimentAggregateResult;
import com.stock.dataCollector.persistence.StockInfoRepository;
import com.stock.modelService.service.LstmInferenceService;
import com.stock.modelService.service.SentimentAggregationService;
import com.stock.strategyAnalysis.config.StrategyConfigService;
import com.stock.strategyAnalysis.domain.dto.StockRankingDto;
import com.stock.strategyAnalysis.domain.entity.StrategyConfig;
import com.stock.strategyAnalysis.domain.entity.StockRanking;
import com.stock.strategyAnalysis.domain.vo.SelectionResult;
import com.stock.strategyAnalysis.persistence.RankingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 真实交易候选股票选择器。
 * 基于已训练的 LSTM 模型和近期新闻情感分析生成候选排名，禁止使用随机数或模拟数据。
 *
 * @author mwangli
 * @since 2026-09-25
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StockSelector {

    private static final int SENTIMENT_LOOKBACK_HOURS = 36;
    private static final double LSTM_SCORE_SCALE = 20D;
    private static final double RETURN_TASK_WEIGHT = 0.55D;
    private static final double DIRECTION_TASK_WEIGHT = 0.30D;
    private static final double RISK_TASK_WEIGHT = 0.15D;

    private final ScoreCalculator scoreCalculator;
    private final StrategyConfigService configService;
    private final RankingRepository rankingRepository;
    private final StockInfoRepository stockInfoRepository;
    private final LstmInferenceService lstmInferenceService;
    private final NewsRepository newsRepository;
    private final SentimentAggregationService sentimentAggregationService;

    /**
     * 执行真实模型选股并返回前 N 个候选。
     *
     * @param n 返回的最大候选数量
     * @return 选股执行结果
     */
    public SelectionResult selectTopN(int n) {
        log.info("开始选股, 目标数量: {}", n);
        long startTime = System.currentTimeMillis();

        try {
            StrategyConfig config = configService.getCurrentConfig();
            List<String> stockCodes = getTradableStockCodes();

            if (stockCodes.isEmpty()) {
                log.warn("没有可用的 LSTM ONNX 模型制品");
                return SelectionResult.builder()
                        .success(false)
                        .errorMessage("没有可用的 LSTM ONNX 模型制品")
                        .build();
            }

            Map<String, String> stockNames = stockInfoRepository.findByCodeIn(stockCodes).stream().collect(Collectors.toMap(
                    item -> item.getCode(),
                    item -> item.getName() == null ? item.getCode() : item.getName(),
                    (left, right) -> left,
                    LinkedHashMap::new));
            stockCodes.forEach(code -> stockNames.putIfAbsent(code, code));
            Map<String, Double> lstmPredictions = getLstmPredictions(stockCodes);
            if (lstmPredictions.isEmpty()) {
                throw new IllegalStateException("已训练股票均未能完成 LSTM 推理，停止生成交易候选");
            }
            Map<String, SentimentAggregateResult> sentimentResults =
                    getSentimentResults(new ArrayList<>(lstmPredictions.keySet()));
            sentimentResults.forEach((stockCode, result) -> {
                if (result.isStrongNegative()) {
                    lstmPredictions.remove(stockCode);
                    log.warn("强负面事件阻断买入候选: stockCode={}, event={}",
                            stockCode, result.getStrongNegativeTitle());
                }
            });
            if (lstmPredictions.isEmpty()) {
                throw new IllegalStateException("所有模型候选均被强负面事件或推理异常阻断");
            }
            Map<String, Double> sentimentScores = sentimentResults.entrySet().stream()
                    .filter(entry -> lstmPredictions.containsKey(entry.getKey()))
                    .collect(Collectors.toMap(
                            Map.Entry::getKey,
                            entry -> entry.getValue().getScore(),
                            (left, right) -> left,
                            LinkedHashMap::new));
            Map<String, Double> totalScores = scoreCalculator.calculateTotalScores(
                    lstmPredictions, sentimentScores, config);

            List<StockRankingDto> rankings = rankAndSelect(totalScores, lstmPredictions,
                    sentimentScores, stockNames, n, config);

            saveRankingResults(rankings);

            long costTime = System.currentTimeMillis() - startTime;
            log.info("选股完成, 共 {} 只股票参与, 选出 {} 只, 耗时 {}ms",
                    lstmPredictions.size(), rankings.size(), costTime);

            return SelectionResult.builder()
                    .executeTime(LocalDateTime.now())
                    .totalStocks(lstmPredictions.size())
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
     * 获取单只股票最近一次排名。
     *
     * @param stockCode 股票代码
     * @return 最近一次排名，不存在时返回 null
     */
    public StockRankingDto getStockRanking(String stockCode) {
        StockRanking ranking = rankingRepository.findFirstByStockCodeOrderByCalculateTimeDesc(stockCode);
        return ranking != null ? convertToDto(ranking) : null;
    }

    /**
     * 获取当天最新排名。
     *
     * @return 当天排名列表
     */
    public List<StockRankingDto> getAllRankings() {
        LocalDate today = LocalDate.now();
        return rankingRepository.findFirstByCalculateTimeBetweenOrderByCalculateTimeDesc(
                        today.atStartOfDay(), today.plusDays(1).atStartOfDay())
                .map(latest -> rankingRepository.findByCalculateTimeOrderByRankAsc(latest.getCalculateTime()))
                .orElseGet(List::of)
                .stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }

    /**
     * 获取共享模型覆盖的全市场股票记录。
     */
    private List<String> getTradableStockCodes() {
        if (!lstmInferenceService.hasModel()) {
            return List.of();
        }
        return stockInfoRepository.findAllCodes().stream()
                .filter(code -> code != null && !code.isBlank())
                .sorted()
                .toList();
    }

    /**
     * 执行真实 LSTM 推理，并把预测收益率压缩为零到一之间的排序分数。
     */
    private Map<String, Double> getLstmPredictions(List<String> stockCodes) {
        Map<String, Double> predictions = new LinkedHashMap<>();
        Map<String, LstmPredictionResultDto> batchResults = lstmInferenceService.predictNextBatch(stockCodes);
        batchResults.forEach((code, prediction) -> {
            Double changeRatio = prediction.getPredictedChangeRatio();
            Double directionProbability = prediction.getDirectionProbability();
            Double downsideRisk = prediction.getDownsideRisk();
            if (changeRatio == null || !Double.isFinite(changeRatio)
                    || directionProbability == null || !Double.isFinite(directionProbability)
                    || downsideRisk == null || !Double.isFinite(downsideRisk)) {
                log.warn("跳过无有效多任务预测的股票, stockCode={}", code);
            } else {
                double returnScore = 1D / (1D + Math.exp(-LSTM_SCORE_SCALE * changeRatio));
                double directionScore = Math.max(0D, Math.min(1D, directionProbability));
                double riskScore = 1D - Math.max(0D, Math.min(1D, downsideRisk));
                predictions.put(code, returnScore * RETURN_TASK_WEIGHT
                        + directionScore * DIRECTION_TASK_WEIGHT
                        + riskScore * RISK_TASK_WEIGHT);
            }
        });
        return predictions;
    }

    /**
     * 使用近期真实新闻和公告计算事件级情感结果。
     * 无新闻时返回低置信度中性结果；模型加载或推理失败时终止整次选股。
     */
    private Map<String, SentimentAggregateResult> getSentimentResults(List<String> stockCodes) {
        Map<String, SentimentAggregateResult> sentiments = new LinkedHashMap<>();
        LocalDateTime evaluationTime = LocalDateTime.now();
        LocalDateTime earliestPublishTime = evaluationTime.minusHours(SENTIMENT_LOOKBACK_HOURS);
        for (String code : stockCodes) {
            List<StockNews> newsItems = newsRepository
                    .findTop20ByStockCodeAndPublishTimeAfterOrderByPublishTimeDesc(code, earliestPublishTime);
            sentiments.put(code, sentimentAggregationService.aggregate(newsItems, evaluationTime));
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
            Map<String, String> stockNames,
            int n,
            StrategyConfig config) {

        List<Map.Entry<String, Double>> sortedEntries = totalScores.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .collect(Collectors.toList());

        List<StockRankingDto> rankings = new ArrayList<>();
        int rank = 1;

        for (Map.Entry<String, Double> entry : sortedEntries) {
            if (rank > n) {
                break;
            }

            String stockCode = entry.getKey();
            double totalScore = entry.getValue();

            if (!scoreCalculator.meetsMinScoreRequirement(totalScore, config)) {
                continue;
            }

            rankings.add(StockRankingDto.builder()
                    .stockCode(stockCode)
                    .stockName(stockNames.getOrDefault(stockCode, stockCode))
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
// AI_GENERATE_END ---------
