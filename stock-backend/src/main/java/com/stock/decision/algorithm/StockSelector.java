// AI_GENERATE_START-------------------------------------
package com.stock.decision.algorithm;

import com.stock.model.dto.PredictionCandidateItemDto;

import com.stock.market.dto.MarketNewsEventDto;
import com.stock.market.dto.StockInfoItemDto;
import com.stock.market.enums.NewsDocumentRole;
import com.stock.market.service.MarketDataQueryService;
import com.stock.market.service.TradingCalendarService;
import com.stock.model.dto.PredictionCandidateBatchDto;
import com.stock.model.vo.SentimentAggregateResult;
import com.stock.model.service.PredictionCandidateQueryService;
import com.stock.model.service.SentimentAggregationService;
import com.stock.model.service.SentimentModelRouterService;
import com.stock.decision.config.StrategyConfigManager;
import com.stock.decision.config.BacktestProperties;
import com.stock.decision.dto.StockRankingDto;
import com.stock.decision.entity.StockRanking;
import com.stock.decision.entity.StrategyConfig;
import com.stock.decision.vo.SelectionResult;
import com.stock.decision.repository.RankingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

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
 * 消费已落库的每日 LSTM 预测批次，并按新闻质量 Gate 开关应用事件排名和风险阻断。
 *
 * @author mwangli
 * @since 2026-09-25
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StockSelector {

    private static final String CANDIDATE_STATUS_ELIGIBLE = "ELIGIBLE";
    private static final String CANDIDATE_STATUS_RANKED = "RANKED";
    private static final int SENTIMENT_LOOKBACK_HOURS = 36;
    private static final double LSTM_SCORE_SCALE = 20D;
    private static final double RETURN_TASK_WEIGHT = 0.55D;
    private static final double DIRECTION_TASK_WEIGHT = 0.30D;
    private static final double RISK_TASK_WEIGHT = 0.15D;

    private final ScoreCalculator scoreCalculator;
    private final StrategyConfigManager configService;
    private final RankingRepository rankingRepository;
    private final MarketDataQueryService marketDataQueryService;
    private final PredictionCandidateQueryService predictionCandidateQueryService;
    private final SentimentAggregationService sentimentAggregationService;
    private final SentimentModelRouterService sentimentModelRouterService;
    private final BacktestProperties backtestProperties;
    private final TradingCalendarService tradingCalendarService;

    /** 数据 Gate 通过前保持关闭，避免未经验证的事件事实改变横截面排名。 */
    @Value("${app.runtime.news.collection-quality.ranking-enabled:false}")
    private boolean newsRankingEnabled;

    /** 风险召回和误报 Gate 通过前保持关闭，避免规则误判直接阻断交易候选。 */
    @Value("${app.runtime.news.collection-quality.risk-gate-enabled:false}")
    private boolean newsRiskGateEnabled;

    /**
     * 执行真实模型选股并返回前 N 个候选。
     *
     * @param n 返回的最大候选数量
     * @return 选股执行结果
     */
    public SelectionResult selectTopN(int n) {
        LocalDate targetTradeDate = getCurrentTargetTradeDate();
        log.info("开始生成交易候选, targetTradeDate={}, targetCount={}", targetTradeDate, n);
        long startTime = System.currentTimeMillis();

        try {
            StrategyConfig config = configService.getCurrentConfig();
            PredictionCandidateBatchDto predictionBatch =
                    predictionCandidateQueryService.getLatestUsableBatch(targetTradeDate);
            validatePredictionBatch(predictionBatch);

            Map<String, String> stockNames = loadStockNames(predictionBatch.getItems());
            Map<String, Double> lstmScores = calculateLstmScores(predictionBatch.getItems());
            if (lstmScores.isEmpty()) {
                throw new IllegalStateException("预测收益扣除交易成本和安全边际后没有可选股票");
            }
            filterByMinimumListingYears(lstmScores, config.getMinimumListingYears(), predictionBatch.getTargetTradeDate());
            if (lstmScores.isEmpty()) {
                throw new IllegalStateException("所有模型候选均不满足最小上市年限条件");
            }
            LocalDateTime informationCutoffAt = LocalDateTime.now();
            boolean ensembleWithNewsFeatures = predictionBatch.isNewsFeaturesEmbedded()
                    && predictionBatch.getItems().stream()
                    .anyMatch(item -> item.getEnsembleRankScore() != null);
            boolean standaloneNewsRankingEnabled = newsRankingEnabled && !ensembleWithNewsFeatures;
            NewsFeatureSnapshot newsSnapshot = loadNewsFeatureSnapshot(
                    new ArrayList<>(lstmScores.keySet()), informationCutoffAt,
                    standaloneNewsRankingEnabled);
            Map<String, SentimentAggregateResult> sentimentResults = newsSnapshot.sentimentResults();

            newsSnapshot.riskEvents().forEach((stockCode, fact) -> {
                lstmScores.remove(stockCode);
                log.warn("重大风险事件阻断买入候选: stockCode={}, canonicalEventId={}, title={}",
                        stockCode, fact.getCanonicalEventId(), fact.getNormalizedTitle());
            });
            if (lstmScores.isEmpty()) {
                throw new IllegalStateException("所有模型候选均被重大风险事件阻断");
            }

            String sentimentModelVersion = standaloneNewsRankingEnabled
                    ? resolveSentimentModelVersion()
                    : ensembleWithNewsFeatures ? "EMBEDDED_IN_TEMPORAL_MODELS"
                    : "DISABLED_PENDING_NEWS_QUALITY_GATE";
            Map<String, Double> newsImpactScores = sentimentResults.entrySet().stream()
                    .filter(entry -> standaloneNewsRankingEnabled)
                    .filter(entry -> lstmScores.containsKey(entry.getKey()))
                    .filter(entry -> entry.getValue().isActionable())
                    .collect(Collectors.toMap(
                            Map.Entry::getKey,
                            entry -> entry.getValue().getNewsImpactScore(),
                            (left, right) -> left,
                            LinkedHashMap::new));
            Map<String, Double> totalScores = scoreCalculator.calculateTotalScores(
                    lstmScores, newsImpactScores, config);
            String candidateBatchId = UUID.randomUUID().toString();
            LocalDateTime calculatedAt = LocalDateTime.now();
            Map<String, Double> currentPrices = predictionBatch.getItems().stream()
                    .filter(item -> item.getCurrentPrice() != null)
                    .collect(Collectors.toMap(
                            PredictionCandidateItemDto::getStockCode,
                            PredictionCandidateItemDto::getCurrentPrice,
                            (left, right) -> left,
                            LinkedHashMap::new));
            List<StockRankingDto> rankings = rankAndSelect(
                    totalScores, lstmScores, newsImpactScores, currentPrices, stockNames, n, config,
                    predictionBatch, sentimentModelVersion, candidateBatchId,
                    informationCutoffAt, calculatedAt);

            saveRankingResults(rankings);
            long costTime = System.currentTimeMillis() - startTime;
            log.info("交易候选生成完成, candidateBatchId={}, predictionBatchId={}, count={}, costMs={}",
                    candidateBatchId, predictionBatch.getBatchId(), rankings.size(), costTime);
            return SelectionResult.builder()
                    .executeTime(calculatedAt)
                    .totalStocks(lstmScores.size())
                    .topN(rankings.stream()
                            .filter(item -> CANDIDATE_STATUS_ELIGIBLE.equals(item.getStatus()))
                            .toList())
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
     * 获取今天目标交易日的完整评分排名。
     *
     * @return 当日完整评分列表
     */
    public List<StockRankingDto> getAllRankings() {
        return getRankings(getCurrentTargetTradeDate());
    }

    /**
     * 解析当前日期对应的候选目标交易日。
     * 周末或节假日展示最近一次面向下个交易日的预测和候选，避免非交易日因日期不匹配显示为空。
     *
     * @return 当前日期对应的目标交易日
     */
    public LocalDate getCurrentTargetTradeDate() {
        LocalDate today = LocalDate.now();
        return tradingCalendarService.isTradingDay(today)
                ? today : tradingCalendarService.nextTradingDay(today);
    }

    /**
     * 获取指定目标交易日的最新可执行候选。
     *
     * @param targetTradeDate 目标交易日
     * @return 元数据完整且状态为可执行的候选列表
     */
    public List<StockRankingDto> getExecutableRankings(LocalDate targetTradeDate) {
        return getRankings(targetTradeDate).stream()
                .filter(item -> CANDIDATE_STATUS_ELIGIBLE.equals(item.getStatus()))
                .toList();
    }

    /**
     * 查询指定候选批次的完整排名 DTO。
     *
     * @param candidateBatchId 候选批次标识
     * @return 按排名升序排列的候选
     */
    public List<StockRankingDto> getRankingsByCandidateBatchId(String candidateBatchId) {
        return rankingRepository.findByCandidateBatchIdOrderByRankAsc(candidateBatchId).stream()
                .map(this::convertToDto).toList();
    }

    /**
     * 查询目标交易日最新候选批次标识。
     *
     * @param targetTradeDate 目标交易日
     * @return 候选批次标识，不存在时返回 null
     */
    public String getLatestCandidateBatchId(LocalDate targetTradeDate) {
        return rankingRepository.findFirstByTargetTradeDateOrderByCalculateTimeDesc(targetTradeDate)
                .map(StockRanking::getCandidateBatchId).orElse(null);
    }

    private List<StockRankingDto> getRankings(LocalDate targetTradeDate) {
        return rankingRepository.findFirstByTargetTradeDateOrderByCalculateTimeDesc(targetTradeDate)
                .map(latest -> rankingRepository.findByCandidateBatchIdOrderByRankAsc(latest.getCandidateBatchId()))
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
            List<PredictionCandidateItemDto> items) {
        List<String> stockCodes = items.stream()
                .map(PredictionCandidateItemDto::getStockCode)
                .toList();
        Map<String, String> stockNames = marketDataQueryService.findStocks(stockCodes).stream()
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
            List<PredictionCandidateItemDto> items) {
        Map<String, Double> scores = new LinkedHashMap<>();
        for (PredictionCandidateItemDto item : items) {
            if (item.getEstimatedNetExpectedReturn() == null
                    || !Double.isFinite(item.getEstimatedNetExpectedReturn())
                    || item.getEstimatedNetExpectedReturn()
                    < backtestProperties.getMinimumCandidateNetReturnRate().doubleValue()) {
                continue;
            }
            double absoluteReturnScore = 1D / (1D + Math.exp(-LSTM_SCORE_SCALE * item.getPredictedReturn()));
            double absoluteDirectionScore = clamp(item.getDirectionProbability());
            double returnScore = item.getEnsembleRankScore() == null
                    ? 0.7D * clamp(item.getPredictedReturnPercentile()) + 0.3D * absoluteReturnScore
                    : clamp(item.getEnsembleRankScore());
            double directionScore = 0.7D * clamp(item.getDirectionProbabilityPercentile())
                    + 0.3D * absoluteDirectionScore;
            double riskScore = 1D - clamp(item.getDownsideRisk());
            scores.put(item.getStockCode(), returnScore * RETURN_TASK_WEIGHT
                    + directionScore * DIRECTION_TASK_WEIGHT
                    + riskScore * RISK_TASK_WEIGHT);
        }
        return scores;
    }

    /**
     * 按预测目标交易日过滤上市年限不足或上市日期缺失的候选股票。
     *
     * @param scores 当前模型候选分数
     * @param minimumListingYears 最小上市年限，支持小数，0 表示不启用
     * @param targetTradeDate 候选对应的目标交易日
     */
    private void filterByMinimumListingYears(
            Map<String, Double> scores, double minimumListingYears, LocalDate targetTradeDate) {
        if (minimumListingYears <= 0 || scores.isEmpty()) {
            return;
        }
        Map<String, StockInfoItemDto> stockInfoMap = marketDataQueryService
                .findStocks(new ArrayList<>(scores.keySet()))
                .stream()
                .collect(Collectors.toMap(
                        StockInfoItemDto::getCode,
                        item -> item,
                        (left, right) -> left,
                        LinkedHashMap::new));
        LocalDate evaluationDate = targetTradeDate == null ? LocalDate.now() : targetTradeDate;
        long minimumListingMonths = Math.max(1L, Math.round(minimumListingYears * 12D));
        List<String> rejectedCodes = scores.keySet().stream()
                .filter(stockCode -> {
                    StockInfoItemDto stockInfo = stockInfoMap.get(stockCode);
                    LocalDate listedDate = stockInfo == null ? null : stockInfo.getListedDate();
                    return listedDate == null
                            || evaluationDate.isBefore(listedDate.plusMonths(minimumListingMonths));
                })
                .toList();
        rejectedCodes.forEach(scores::remove);
        log.info("上市年限门槛过滤完成: minimumListingYears={}, rejected={}, remaining={}",
                minimumListingYears, rejectedCodes.size(), scores.size());
    }

    private double clamp(double value) {
        return Math.max(0D, Math.min(1D, value));
    }

    private NewsFeatureSnapshot loadNewsFeatureSnapshot(
            List<String> stockCodes, LocalDateTime evaluationTime, boolean includeRanking) {
        if (!includeRanking && !newsRiskGateEnabled) {
            return new NewsFeatureSnapshot(Map.of(), Map.of());
        }
        Map<String, SentimentAggregateResult> sentiments = new LinkedHashMap<>();
        LocalDateTime earliestPublishTime = evaluationTime.minusHours(SENTIMENT_LOOKBACK_HOURS);
        List<NewsDocumentRole> enabledRoles = new ArrayList<>();
        if (includeRanking) {
            enabledRoles.add(NewsDocumentRole.TRADE_SIGNAL);
        }
        if (newsRiskGateEnabled) {
            enabledRoles.add(NewsDocumentRole.RISK_ONLY);
        }
        List<MarketNewsEventDto> eventFacts = marketDataQueryService.findNewsEvents(
                        stockCodes, earliestPublishTime, evaluationTime, enabledRoles);
        Map<String, List<MarketNewsEventDto>> tradeSignalsByStock = eventFacts.stream()
                .filter(fact -> fact.getDocumentRole() == NewsDocumentRole.TRADE_SIGNAL)
                .collect(Collectors.groupingBy(MarketNewsEventDto::getStockCode, LinkedHashMap::new,
                        Collectors.collectingAndThen(Collectors.toList(), items -> items.stream()
                                .limit(20).toList())));
        if (includeRanking) {
            tradeSignalsByStock.forEach((stockCode, facts) -> sentiments.put(
                    stockCode, sentimentAggregationService.aggregateEventFacts(facts, evaluationTime)));
        }
        Map<String, MarketNewsEventDto> riskEvents = newsRiskGateEnabled
                ? eventFacts.stream()
                        .filter(fact -> fact.getDocumentRole() == NewsDocumentRole.RISK_ONLY)
                        .collect(Collectors.toMap(
                                MarketNewsEventDto::getStockCode,
                                fact -> fact,
                                (first, ignored) -> first,
                                LinkedHashMap::new))
                : Map.of();
        return new NewsFeatureSnapshot(sentiments, riskEvents);
    }

    private String resolveSentimentModelVersion() {
        String version = sentimentModelRouterService.activeModelVersion();
        if (version == null || version.isBlank()) {
            throw new IllegalStateException("情感模型未提供可追溯版本，停止生成交易候选");
        }
        return version;
    }

    private List<StockRankingDto> rankAndSelect(
            Map<String, Double> totalScores,
            Map<String, Double> lstmScores,
            Map<String, Double> newsImpactScores,
            Map<String, Double> currentPrices,
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
            boolean eligible = rank <= n
                    && scoreCalculator.meetsMinScoreRequirement(entry.getValue(), config);
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
                    .status(eligible ? CANDIDATE_STATUS_ELIGIBLE : CANDIDATE_STATUS_RANKED)
                    .stockCode(entry.getKey())
                    .stockName(stockNames.getOrDefault(entry.getKey(), entry.getKey()))
                    .currentPrice(currentPrices.get(entry.getKey()))
                    .lstmScore(lstmScores.get(entry.getKey()))
                    .newsImpactScore(newsImpactScores.getOrDefault(entry.getKey(), 0D))
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

    /** 新闻事件事实在单次选股时点的分流快照。 */
    private record NewsFeatureSnapshot(
            Map<String, SentimentAggregateResult> sentimentResults,
            Map<String, MarketNewsEventDto> riskEvents) {
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
                .currentPrice(dto.getCurrentPrice())
                .lstmScore(dto.getLstmScore())
                .newsImpactScore(dto.getNewsImpactScore())
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
                .currentPrice(ranking.getCurrentPrice())
                .lstmScore(ranking.getLstmScore())
                .newsImpactScore(ranking.getNewsImpactScore())
                .totalScore(ranking.getTotalScore())
                .rank(ranking.getRank())
                .calculateTime(ranking.getCalculateTime())
                .reason(ranking.getReason())
                .build();
    }
}
// AI_GENERATE_END-------------------------------------
