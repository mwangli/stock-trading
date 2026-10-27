// AI_GENERATE_START- ------
package com.stock.model.service;

import com.stock.data.dto.MarketNewsEventDto;
import com.stock.data.enums.NewsDocumentRole;
import com.stock.data.service.MarketDataQueryService;
import com.stock.model.dto.NewsEventFeatureVector;
import com.stock.model.vo.SentimentAggregateResult;
import com.stock.model.vo.SentimentAnalysisResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.ArrayList;
import java.util.Set;

/**
 * 将规范新闻事件事实转换为点时安全的时序模型特征。
 *
 * @author mwangli
 * @since 2026-10-24
 */
@Service
@RequiredArgsConstructor
public class NewsEventFeatureService {

    private static final int LOOKBACK_DAYS = 5;
    private static final double LOG_TWO = Math.log(2D);
    private static final List<String> EVENT_TYPES = List.of(
            "EARNINGS", "REGULATORY_OR_LITIGATION", "CAPITAL_RISK",
            "SHAREHOLDER_OR_EXECUTIVE", "MATERIAL_BUSINESS", "MACRO_OR_INDUSTRY", "OTHER");

    private final MarketDataQueryService marketDataQueryService;
    private final SentimentAggregationService sentimentAggregationService;
    private final SentimentModelRouterService sentimentModelRouterService;

    /**
     * 生成单只股票在指定交易日截止时点的新闻事件特征。
     *
     * @param stockCode 股票代码
     * @param featureTradeDate 特征归属交易日
     * @param cutoffTime 信息截止时间
     * @return 固定结构的新闻事件特征
     * @throws IllegalArgumentException 股票代码、交易日或截止时间无效时抛出
     */
    public NewsEventFeatureVector buildFeature(
            String stockCode, LocalDate featureTradeDate, LocalDateTime cutoffTime) {
        if (stockCode == null || stockCode.isBlank()
                || featureTradeDate == null || cutoffTime == null) {
            throw new IllegalArgumentException("新闻事件特征必须提供股票代码、交易日和截止时间");
        }
        List<MarketNewsEventDto> tradeSignals = marketDataQueryService.findNewsEvents(
                        List.of(stockCode.trim()), cutoffTime.minusDays(LOOKBACK_DAYS), cutoffTime,
                        List.of(NewsDocumentRole.TRADE_SIGNAL));
        SentimentAggregateResult aggregate = sentimentAggregationService
                .aggregateEventFacts(tradeSignals, cutoffTime);
        boolean noEffectiveEvent = tradeSignals.isEmpty();
        List<SentimentAnalysisResult> modelResults = tradeSignals.stream()
                .map(item -> sentimentModelRouterService.analyzeRequired(
                        item.getNormalizedTitle(), item.getNormalizedContent()))
                .toList();
        return NewsEventFeatureVector.builder()
                .stockCode(stockCode.trim())
                .featureTradeDate(featureTradeDate)
                .cutoffTime(cutoffTime)
                .impactDirection(directionValue(aggregate.getImpactDirection()))
                .impactMagnitude(aggregate.getImpactMagnitude())
                .materiality(average(tradeSignals, true))
                .novelty(average(tradeSignals, false))
                .confidence(aggregate.getConfidence())
                .eventAgeHours(eventAgeHours(tradeSignals, cutoffTime))
                .riskFlag(false)
                .eventTypeCounts(eventTypeCounts(tradeSignals))
                .noEffectiveEvent(noEffectiveEvent)
                .missingMask(noEffectiveEvent ? 1 : 0)
                .pipelineVersion(resolvePipelineVersion(tradeSignals))
                .inputEventCount(tradeSignals.size())
                .latestInputPublishTime(latestPublishTime(tradeSignals))
                .sentimentModelVersion(noEffectiveEvent ? null
                        : sentimentModelRouterService.activeModelVersion())
                .temporalFeatures(buildTemporalFeatures(
                        tradeSignals, modelResults, aggregate, cutoffTime, noEffectiveEvent))
                .build();
    }

    private List<Double> buildTemporalFeatures(
            List<MarketNewsEventDto> facts,
            List<SentimentAnalysisResult> results,
            SentimentAggregateResult aggregate,
            LocalDateTime cutoffTime,
            boolean missing) {
        List<Double> features = new ArrayList<>(NewsFeatureSnapshotService.FEATURE_COUNT);
        features.add(averageProbability(results, "positive"));
        features.add(averageProbability(results, "neutral"));
        features.add(averageProbability(results, "negative"));
        features.add(results.stream().mapToDouble(SentimentAnalysisResult::getScore).average().orElse(0D));
        features.add(results.stream().mapToDouble(SentimentAnalysisResult::getCalibratedConfidence)
                .average().orElse(0D));
        features.add(results.stream().mapToDouble(SentimentAnalysisResult::getEntropy).average().orElse(0D));
        features.add(results.stream().mapToDouble(SentimentAnalysisResult::getProbabilityMargin)
                .average().orElse(0D));
        features.add(rate(results.stream().filter(SentimentAnalysisResult::isAbstained).count(), results.size()));
        features.add(Math.min(1D, facts.size() / 5D));
        features.add(aggregate.getImpactMagnitude());
        features.add(average(facts, true));
        features.add(average(facts, false));
        features.add(facts.stream().map(MarketNewsEventDto::getStockSpecificity)
                .filter(Objects::nonNull).mapToDouble(Double::doubleValue).average().orElse(0D));
        features.add(averageTimeDecay(facts, cutoffTime));
        features.add(Math.min(1D, eventAgeHours(facts, cutoffTime) / (LOOKBACK_DAYS * 24D)));
        features.add(Math.min(1D, Math.log1p(facts.size()) / Math.log(6D)));
        features.add(sourceQuality(facts));
        Map<String, Integer> typeCounts = eventTypeCounts(facts);
        for (String eventType : EVENT_TYPES) {
            int count = "OTHER".equals(eventType)
                    ? facts.size() - typeCounts.entrySet().stream()
                    .filter(entry -> !"OTHER".equals(normalizeEventType(entry.getKey())))
                    .mapToInt(Map.Entry::getValue).sum()
                    : typeCounts.getOrDefault(eventType, 0);
            features.add(rate(Math.max(0, count), facts.size()));
        }
        features.add(missing ? 1D : 0D);
        if (features.size() != NewsFeatureSnapshotService.FEATURE_COUNT) {
            throw new IllegalStateException("temporal-news-v1 特征维度错误: " + features.size());
        }
        return List.copyOf(features);
    }

    private double averageProbability(List<SentimentAnalysisResult> results, String label) {
        return results.stream().map(SentimentAnalysisResult::getProbabilities)
                .filter(Objects::nonNull).map(map -> map.get(label)).filter(Objects::nonNull)
                .mapToDouble(Double::doubleValue).average().orElse(0D);
    }

    private double averageTimeDecay(List<MarketNewsEventDto> facts, LocalDateTime cutoffTime) {
        return facts.stream().map(MarketNewsEventDto::getPublishTime).filter(Objects::nonNull)
                .mapToDouble(time -> Math.exp(-LOG_TWO * Math.max(0D,
                        Duration.between(time, cutoffTime).toMinutes() / 60D) / 24D))
                .average().orElse(0D);
    }

    private double sourceQuality(List<MarketNewsEventDto> facts) {
        return facts.stream().map(MarketNewsEventDto::getSources).filter(Objects::nonNull)
                .flatMap(Set::stream).mapToDouble(source -> {
                    if (source.contains("证券交易所") || source.contains("证监会")) {
                        return 1D;
                    }
                    if (source.contains("公告") || source.contains("上市公司")) {
                        return 0.9D;
                    }
                    return 0.6D;
                }).average().orElse(0D);
    }

    private String normalizeEventType(String eventType) {
        return EVENT_TYPES.contains(eventType) ? eventType : "OTHER";
    }

    private double rate(long numerator, long denominator) {
        return denominator <= 0 ? 0D : numerator / (double) denominator;
    }

    private double average(List<MarketNewsEventDto> facts, boolean materiality) {
        return facts.stream()
                .map(item -> materiality ? item.getMaterialityScore() : item.getNoveltyScore())
                .filter(Objects::nonNull)
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(0D);
    }

    private double eventAgeHours(List<MarketNewsEventDto> facts, LocalDateTime cutoffTime) {
        return facts.stream()
                .map(MarketNewsEventDto::getPublishTime)
                .filter(Objects::nonNull)
                .mapToLong(time -> Math.max(0L, Duration.between(time, cutoffTime).toMinutes()))
                .min()
                .orElse(0L) / 60D;
    }

    private Map<String, Integer> eventTypeCounts(List<MarketNewsEventDto> facts) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        facts.stream()
                .map(MarketNewsEventDto::getEventType)
                .filter(type -> type != null && !type.isBlank())
                .forEach(type -> counts.merge(type, 1, Integer::sum));
        return counts;
    }

    private LocalDateTime latestPublishTime(List<MarketNewsEventDto> facts) {
        return facts.stream()
                .map(MarketNewsEventDto::getPublishTime)
                .filter(Objects::nonNull)
                .max(LocalDateTime::compareTo)
                .orElse(null);
    }
    private String resolvePipelineVersion(List<MarketNewsEventDto> facts) {
        List<String> versions = facts.stream()
                .map(MarketNewsEventDto::getPipelineVersion)
                .filter(value -> value != null && !value.isBlank())
                .distinct()
                .toList();
        if (versions.isEmpty()) {
            return null;
        }
        return versions.size() == 1 ? versions.get(0) : "MIXED";
    }

    private double directionValue(String direction) {
        if ("BULLISH".equals(direction)) {
            return 1D;
        }
        if ("BEARISH".equals(direction)) {
            return -1D;
        }
        return 0D;
    }
}
// AI_GENERATE_END- ------
