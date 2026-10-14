// AI_GENERATE_START ------------
package com.stock.strategyAnalysis.engine;

import com.stock.dataCollector.domain.entity.StockNews;
import com.stock.dataCollector.persistence.NewsRepository;
import com.stock.dataCollector.persistence.StockInfoRepository;
import com.stock.modelService.domain.dto.PredictionCandidateBatchDto;
import com.stock.modelService.domain.vo.SentimentAggregateResult;
import com.stock.modelService.service.PredictionCandidateQueryService;
import com.stock.modelService.service.SentimentAggregationService;
import com.stock.modelService.service.SentimentInferenceService;
import com.stock.strategyAnalysis.config.StrategyConfigService;
import com.stock.strategyAnalysis.domain.dto.StockRankingDto;
import com.stock.strategyAnalysis.domain.entity.StockRanking;
import com.stock.strategyAnalysis.domain.entity.StrategyConfig;
import com.stock.strategyAnalysis.domain.vo.SelectionResult;
import com.stock.strategyAnalysis.persistence.RankingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 真实交易候选股票选择器。
 * 消费已落库的每日 LSTM 预测批次，并结合严格情感模型结果生成可追溯候选。
 *
 * @author mwangli
 * @since 2026-09-25
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StockSelector {

    private static final String CANDIDATE_STATUS_ELIGIBLE = "ELIGIBLE";
    private static final int SENTIMENT_LOOKBACK_HOURS = 36;
    private static final double LSTM_SCORE_SCALE = 20D;
    private static final double RETURN_TASK_WEIGHT = 0.55D;
    private static final double DIRECTION_TASK_WEIGHT = 0.30D;
    private static final double RISK_TASK_WEIGHT = 0.15D;

    private final ScoreCalculator scoreCalculator;
    private final StrategyConfigService configService;
    private final RankingRepository rankingRepository;
    private final StockInfoRepository stockInfoRepository;
    private final NewsRepository newsRepository;
    private final PredictionCandidateQueryService predictionCandidateQueryService;
    private final SentimentAggregationService sentimentAggregationService;
    private final SentimentInferenceService sentimentInferenceService;

    /**
     * 执行真实模型选股并返回前 N 个候选。
     *
     * @param n 返回的最大候选数量
     * @return 选股执行结果
     */
    public SelectionResult selectTopN(int n) {
        LocalDate targetTradeDate = LocalDate.now();
        log.info("开始生成交易候选, targetTradeDate={}, targetCount={}", targetTradeDate, n);
        long startTime = System.currentTimeMillis();

        try {
            StrategyConfig config = configService.getCurrentConfig();
            if (!config.isEnabled()) {
                return SelectionResult.builder().success(false).errorMessage("策略总开关已关闭").build();
            }
            PredictionCandidateBatchDto predictionBatch =
                    predictionCandidateQueryService.getLatestUsableBatch(targetTradeDate);
            validatePredictionBatch(predictionBatch);

            Map<String, String> stockNames = loadStockNames(predictionBatch.getItems());
            Map<String, Double> lstmScores = calculateLstmScores(predictionBatch.getItems());
            LocalDateTime informationCutoffAt = LocalDateTime.now();
            Map<String, SentimentAggregateResult> sentimentResults =
                    getSentimentResults(new ArrayList<>(lstmScores.keySet()), informationCutoffAt);

            sentimentResults.forEach((stockCode, result) -> {
                if (result.isStrongNegative()) {
                    lstmScores.remove(stockCode);
                    log.warn("强负面事件阻断买入候选: stockCode={}, event={}",
                            stockCode, result.getStrongNegativeTitle());
                }
            });
            if (lstmScores.isEmpty()) {
                throw new IllegalStateException("所有模型候选均被强负面事件阻断");
            }

            String sentimentModelVersion = resolveSentimentModelVersion();
            Map<String, Double> sentimentScores = sentimentResults.entrySet().stream()
                    .filter(entry -> lstmScores.containsKey(entry.getKey()))
                    .collect(Collectors.toMap(
                            Map.Entry::getKey,
                            entry -> entry.getValue().getScore(),
                            (left, right) -> left,
                            LinkedHashMap::new));
            Map<String, Double> totalScores = scoreCalculator.calculateTotalScores(
                    lstmScores, sentimentScores, config);
            String candidateBatchId = UUID.randomUUID().toString();
            LocalDateTime calculatedAt = LocalDateTime.now();
            List<StockRankingDto> rankings = rankAndSelect(
                    totalScores, lstmScores, sentimentScores, stockNames, n, config,
                    predictionBatch, sentimentModelVersion, candidateBatchId,
                    informationCutoffAt, calculatedAt);

            saveRankingResults(rankings);
            long costTime = System.currentTimeMillis() - startTime;
            log.info("交易候选生成完成, candidateBatchId={}, predictionBatchId={}, count={}, costMs={}",
                    candidateBatchId, predictionBatch.getBatchId(), rankings.size(), costTime);
            return SelectionResult.builder()
                    .executeTime(calculatedAt)
                    .totalStocks(lstmScores.size())
                    .topN(rankings)
                    .costTimeMs(costTime)
                    .success(true)
                    .build();
        } catch (Exception exception) {
            log.error("交易候选生成失败", exception);
            return SelectionResult.builder().success(false).errorMessage(exception.getMessage()).build();
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
     * 获取今天目标交易日的最新可执行候选。
     *
     * @return 当日候选列表
     */
    public List<StockRankingDto> getAllRankings() {
        return getExecutableRankings(LocalDate.now());
    }

    /**
     * 获取指定目标交易日的最新可执行候选。
     *
     * @param targetTradeDate 目标交易日
     * @return 元数据完整且状态为可执行的候选列表
     */
    public List<StockRankingDto> getExecutableRankings(LocalDate targetTradeDate) {
        return rankingRepository.findFirstByTargetTradeDateOrderByCalculateTimeDesc(targetTradeDate)
                .map(latest -> rankingRepository.findByCandidateBatchIdAndStatusOrderByRankAsc(
                        latest.getCandidateBatchId(), CANDIDATE_STATUS_ELIGIBLE))
                .orElseGet(List::of)
                .stream()
                .filter(this::hasCompleteTraceMetadata)
                .map(this::convertToDto)
                .toList();
    }

    private void validatePredictionBatch(PredictionCandidateBatchDto batch) {
        if (batch.getBatchId() == null || batch.getBatchId().isBlank()
                || batch.getModelId() == null || batch.getModelId().isBlank()
                || batch.getModelVersion() == null || batch.getModelVersion().isBlank()
                || batch.getBaseTradeDate() == null || batch.getTargetTradeDate() == null
                || batch.getInformationCutoffAt() == null || batch.getItems() == null
                || batch.getItems().isEmpty()) {
            throw new IllegalStateException("每日预测批次缺少生产候选所需的追溯信息");
        }
    }

    private Map<String, String> loadStockNames(
            List<PredictionCandidateBatchDto.PredictionCandidateItemDto> items) {
        List<String> stockCodes = items.stream()
                .map(PredictionCandidateBatchDto.PredictionCandidateItemDto::getStockCode)
                .toList();
        Map<String, String> stockNames = stockInfoRepository.findByCodeIn(stockCodes).stream()
                .collect(Collectors.toMap(
                        item -> item.getCode(),
                        item -> item.getName() == null ? item.getCode() : item.getName(),
                        (left, right) -> left,
                        LinkedHashMap::new));
        items.forEach(item -> stockNames.putIfAbsent(item.getStockCode(),
                item.getStockName() == null ? item.getStockCode() : item.getStockName()));
        return stockNames;
    }

    private Map<String, Double> calculateLstmScores(
            List<PredictionCandidateBatchDto.PredictionCandidateItemDto> items) {
        Map<String, Double> scores = new LinkedHashMap<>();
        for (PredictionCandidateBatchDto.PredictionCandidateItemDto item : items) {
            double returnScore = 1D / (1D + Math.exp(-LSTM_SCORE_SCALE * item.getPredictedReturn()));
            double directionScore = clamp(item.getDirectionProbability());
            double riskScore = 1D - clamp(item.getDownsideRisk());
            scores.put(item.getStockCode(), returnScore * RETURN_TASK_WEIGHT
                    + directionScore * DIRECTION_TASK_WEIGHT
                    + riskScore * RISK_TASK_WEIGHT);
        }
        return scores;
    }

    private double clamp(double value) {
        return Math.max(0D, Math.min(1D, value));
    }

    private Map<String, SentimentAggregateResult> getSentimentResults(
            List<String> stockCodes, LocalDateTime evaluationTime) {
        Map<String, SentimentAggregateResult> sentiments = new LinkedHashMap<>();
        LocalDateTime earliestPublishTime = evaluationTime.minusHours(SENTIMENT_LOOKBACK_HOURS);
        Map<String, List<StockNews>> newsByStock = newsRepository
                .findByStockCodeInAndPublishTimeAfterOrderByPublishTimeDesc(stockCodes, earliestPublishTime)
                .stream()
                .collect(Collectors.groupingBy(StockNews::getStockCode, LinkedHashMap::new,
                        Collectors.collectingAndThen(Collectors.toList(), items -> items.stream()
                                .limit(20).toList())));
        for (String code : stockCodes) {
            List<StockNews> newsItems = newsByStock.getOrDefault(code, List.of());
            sentiments.put(code, sentimentAggregationService.aggregate(newsItems, evaluationTime));
        }
        return sentiments;
    }

    private String resolveSentimentModelVersion() {
        String version = sentimentInferenceService.getArtifactInspection().getModelVersion();
        if (version == null || version.isBlank()) {
            throw new IllegalStateException("情感模型未提供可追溯版本，停止生成交易候选");
        }
        return version;
    }

    private List<StockRankingDto> rankAndSelect(
            Map<String, Double> totalScores,
            Map<String, Double> lstmScores,
            Map<String, Double> sentimentScores,
            Map<String, String> stockNames,
            int n,
            StrategyConfig config,
            PredictionCandidateBatchDto predictionBatch,
            String sentimentModelVersion,
            String candidateBatchId,
            LocalDateTime informationCutoffAt,
            LocalDateTime calculatedAt) {
        List<Map.Entry<String, Double>> sortedEntries = totalScores.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed()
                        .thenComparing(Map.Entry::getKey, Comparator.naturalOrder()))
                .toList();

        List<StockRankingDto> rankings = new ArrayList<>();
        int rank = 1;
        for (Map.Entry<String, Double> entry : sortedEntries) {
            if (rank > n) {
                break;
            }
            if (!scoreCalculator.meetsMinScoreRequirement(entry.getValue(), config)) {
                continue;
            }
            rankings.add(StockRankingDto.builder()
                    .candidateBatchId(candidateBatchId)
                    .predictionBatchId(predictionBatch.getBatchId())
                    .modelId(predictionBatch.getModelId())
                    .modelVersion(predictionBatch.getModelVersion())
                    .sentimentModelVersion(sentimentModelVersion)
                    .strategyConfigVersion(config.getVersion())
                    .baseTradeDate(predictionBatch.getBaseTradeDate())
                    .targetTradeDate(predictionBatch.getTargetTradeDate())
                    .informationCutoffAt(informationCutoffAt)
                    .status(CANDIDATE_STATUS_ELIGIBLE)
                    .stockCode(entry.getKey())
                    .stockName(stockNames.getOrDefault(entry.getKey(), entry.getKey()))
                    .lstmScore(lstmScores.get(entry.getKey()))
                    .sentimentScore(sentimentScores.get(entry.getKey()))
                    .totalScore(entry.getValue())
                    .rank(rank)
                    .calculateTime(calculatedAt)
                    .reason(generateReason(entry.getValue(), rank))
                    .build());
            rank++;
        }
        return rankings;
    }

    private String generateReason(double totalScore, int rank) {
        if (totalScore >= 0.7) {
            return String.format("综合得分%.2f, 排名第%d, 强烈推荐买入", totalScore, rank);
        }
        if (totalScore >= 0.5) {
            return String.format("综合得分%.2f, 排名第%d, 推荐买入", totalScore, rank);
        }
        return String.format("综合得分%.2f, 排名第%d, 建议关注", totalScore, rank);
    }

    private void saveRankingResults(List<StockRankingDto> rankings) {
        List<StockRanking> entities = rankings.stream().map(this::toEntity).toList();
        rankingRepository.saveAll(entities);
    }

    private StockRanking toEntity(StockRankingDto dto) {
        return StockRanking.builder()
                .candidateBatchId(dto.getCandidateBatchId())
                .predictionBatchId(dto.getPredictionBatchId())
                .modelId(dto.getModelId())
                .modelVersion(dto.getModelVersion())
                .sentimentModelVersion(dto.getSentimentModelVersion())
                .strategyConfigVersion(dto.getStrategyConfigVersion())
                .baseTradeDate(dto.getBaseTradeDate())
                .targetTradeDate(dto.getTargetTradeDate())
                .informationCutoffAt(dto.getInformationCutoffAt())
                .status(dto.getStatus())
                .stockCode(dto.getStockCode())
                .stockName(dto.getStockName())
                .lstmScore(dto.getLstmScore())
                .sentimentScore(dto.getSentimentScore())
                .totalScore(dto.getTotalScore())
                .rank(dto.getRank())
                .calculateTime(dto.getCalculateTime())
                .reason(dto.getReason())
                .build();
    }

    private boolean hasCompleteTraceMetadata(StockRanking ranking) {
        return ranking.getCandidateBatchId() != null && !ranking.getCandidateBatchId().isBlank()
                && ranking.getPredictionBatchId() != null && !ranking.getPredictionBatchId().isBlank()
                && ranking.getModelVersion() != null && !ranking.getModelVersion().isBlank()
                && ranking.getSentimentModelVersion() != null && !ranking.getSentimentModelVersion().isBlank()
                && ranking.getStrategyConfigVersion() != null && !ranking.getStrategyConfigVersion().isBlank()
                && ranking.getBaseTradeDate() != null && ranking.getTargetTradeDate() != null
                && ranking.getInformationCutoffAt() != null;
    }

    private StockRankingDto convertToDto(StockRanking ranking) {
        return StockRankingDto.builder()
                .candidateBatchId(ranking.getCandidateBatchId())
                .predictionBatchId(ranking.getPredictionBatchId())
                .modelId(ranking.getModelId())
                .modelVersion(ranking.getModelVersion())
                .sentimentModelVersion(ranking.getSentimentModelVersion())
                .strategyConfigVersion(ranking.getStrategyConfigVersion())
                .baseTradeDate(ranking.getBaseTradeDate())
                .targetTradeDate(ranking.getTargetTradeDate())
                .informationCutoffAt(ranking.getInformationCutoffAt())
                .status(ranking.getStatus())
                .stockCode(ranking.getStockCode())
                .stockName(ranking.getStockName())
                .lstmScore(ranking.getLstmScore())
                .sentimentScore(ranking.getSentimentScore())
                .totalScore(ranking.getTotalScore())
                .rank(ranking.getRank())
                .calculateTime(ranking.getCalculateTime())
                .reason(ranking.getReason())
                .build();
    }
}
// AI_GENERATE_END ------------
