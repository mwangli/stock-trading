// AI_GENERATE_START ---
package com.stock.strategyAnalysis.engine;

import com.stock.dataCollector.domain.entity.StockNews;
import com.stock.dataCollector.persistence.NewsRepository;
import com.stock.modelService.domain.dto.LstmPredictionResultDto;
import com.stock.modelService.domain.entity.ModelTrainingRecord;
import com.stock.modelService.domain.vo.SentimentAnalysisResult;
import com.stock.modelService.persistence.ModelTrainingRecordRepository;
import com.stock.modelService.service.LstmTrainerService;
import com.stock.modelService.service.SentimentTrainerService;
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
    private static final int MAX_SENTIMENT_TEXT_LENGTH = 2000;
    private static final double LSTM_SCORE_SCALE = 20D;

    private final ScoreCalculator scoreCalculator;
    private final StrategyConfigService configService;
    private final RankingRepository rankingRepository;
    private final ModelTrainingRecordRepository modelTrainingRecordRepository;
    private final LstmTrainerService lstmTrainerService;
    private final NewsRepository newsRepository;
    private final SentimentTrainerService sentimentTrainerService;

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
            List<ModelTrainingRecord> modelRecords = getTradableStocks();

            if (modelRecords.isEmpty()) {
                log.warn("没有已完成训练的 LSTM 股票模型");
                return SelectionResult.builder()
                        .success(false)
                        .errorMessage("没有已完成训练的 LSTM 股票模型")
                        .build();
            }

            Map<String, String> stockNames = modelRecords.stream().collect(Collectors.toMap(
                    ModelTrainingRecord::getStockCode,
                    record -> record.getStockName() == null ? record.getStockCode() : record.getStockName(),
                    (left, right) -> left,
                    LinkedHashMap::new));
            List<String> stockCodes = new ArrayList<>(stockNames.keySet());
            Map<String, Double> lstmPredictions = getLstmPredictions(stockCodes);
            if (lstmPredictions.isEmpty()) {
                throw new IllegalStateException("已训练股票均未能完成 LSTM 推理，停止生成交易候选");
            }
            Map<String, Double> sentimentScores = getSentimentScores(new ArrayList<>(lstmPredictions.keySet()));
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
     * 获取具有已训练 LSTM 模型的股票记录。
     */
    private List<ModelTrainingRecord> getTradableStocks() {
        return modelTrainingRecordRepository.findByTrainedTrueOrderByStockCodeAsc();
    }

    /**
     * 执行真实 LSTM 推理，并把预测收益率压缩为零到一之间的排序分数。
     */
    private Map<String, Double> getLstmPredictions(List<String> stockCodes) {
        Map<String, Double> predictions = new LinkedHashMap<>();
        for (String code : stockCodes) {
            try {
                LstmPredictionResultDto prediction = lstmTrainerService.predictNext(code);
                Double changeRatio = prediction.getPredictedChangeRatio();
                if (changeRatio == null || !Double.isFinite(changeRatio)) {
                    log.warn("跳过无有效预测收益率的股票, stockCode={}", code);
                    continue;
                }
                double score = 1D / (1D + Math.exp(-LSTM_SCORE_SCALE * changeRatio));
                predictions.put(code, score);
            } catch (RuntimeException exception) {
                log.warn("跳过 LSTM 推理失败的股票, stockCode={}, reason={}", code, exception.getMessage());
            }
        }
        return predictions;
    }

    /**
     * 使用近期真实新闻和公告计算股票情感得分。
     * 无新闻时返回中性分数；模型加载或推理失败时终止整次选股。
     */
    private Map<String, Double> getSentimentScores(List<String> stockCodes) {
        Map<String, Double> sentiments = new LinkedHashMap<>();
        LocalDateTime earliestPublishTime = LocalDateTime.now().minusHours(SENTIMENT_LOOKBACK_HOURS);
        for (String code : stockCodes) {
            List<StockNews> newsItems = newsRepository
                    .findTop20ByStockCodeAndPublishTimeAfterOrderByPublishTimeDesc(code, earliestPublishTime);
            if (newsItems.isEmpty()) {
                sentiments.put(code, 0D);
                continue;
            }

            double weightedScore = 0D;
            double confidenceSum = 0D;
            for (StockNews news : newsItems) {
                String text = buildSentimentText(news);
                if (text.isBlank()) {
                    continue;
                }
                SentimentAnalysisResult result = sentimentTrainerService.analyzeSentimentRequired(text);
                double confidence = Math.max(0D, result.getConfidence());
                weightedScore += result.getScore() * confidence;
                confidenceSum += confidence;
            }
            sentiments.put(code, confidenceSum > 0D ? weightedScore / confidenceSum : 0D);
        }
        return sentiments;
    }

    private String buildSentimentText(StockNews news) {
        String title = news.getTitle() == null ? "" : news.getTitle().trim();
        String content = news.getContent() == null ? "" : news.getContent().trim();
        String text = (title + "。" + content).trim();
        return text.length() <= MAX_SENTIMENT_TEXT_LENGTH
                ? text
                : text.substring(0, MAX_SENTIMENT_TEXT_LENGTH);
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
// AI_GENERATE_END ---
