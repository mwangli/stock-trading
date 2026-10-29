// AI_GENERATE_START---------------
package com.stock.model.service;

import com.stock.data.dto.MarketNewsEventDto;
import com.stock.data.enums.NewsDocumentRole;
import com.stock.data.service.MarketDataQueryService;
import com.stock.model.dto.NewsEventFeatureVector;
import com.stock.model.dto.SentimentModelInput;
import com.stock.model.entity.SentimentEventCacheDocument;
import com.stock.model.repository.SentimentEventCacheRepository;
import com.stock.model.vo.SentimentAggregateResult;
import com.stock.model.vo.SentimentAnalysisResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
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
@Slf4j
@RequiredArgsConstructor
public class NewsEventFeatureService {

    private static final int LOOKBACK_DAYS = 5;
    private static final int MAX_SENTIMENT_CACHE_SIZE = 200_000;
    private static final int PERSISTED_INFERENCE_BATCH_SIZE = 256;
    private static final double LOG_TWO = Math.log(2D);
    private final MarketDataQueryService marketDataQueryService;
    private final SentimentAggregationService sentimentAggregationService;
    private final SentimentModelRouterService sentimentModelRouterService;
    private final SentimentInputAssembler sentimentInputAssembler;
    private final SentimentEventCacheRepository sentimentEventCacheRepository;
    private final Map<String, SentimentAnalysisResult> sentimentResultCache =
            Collections.synchronizedMap(new LinkedHashMap<>(16, 0.75F, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, SentimentAnalysisResult> eldest) {
                    return size() > MAX_SENTIMENT_CACHE_SIZE;
                }
            });

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
        return buildFeature(stockCode, featureTradeDate, cutoffTime, tradeSignals);
    }

    /**
     * 使用已批量加载的事件生成单只股票新闻特征，避免历史回填逐股重复查询。
     *
     * @param stockCode 股票代码
     * @param featureTradeDate 特征归属交易日
     * @param cutoffTime 信息截止时间
     * @param tradeSignals 已按点时窗口加载的交易信号事件
     * @return 固定结构的新闻事件特征
     * @throws IllegalArgumentException 股票代码、交易日或截止时间无效时抛出
     */
    public NewsEventFeatureVector buildFeature(
            String stockCode, LocalDate featureTradeDate, LocalDateTime cutoffTime,
            List<MarketNewsEventDto> tradeSignals) {
        return buildFeature(stockCode, featureTradeDate, cutoffTime, tradeSignals, null);
    }

    /**
     * 使用批量预加载的情感模型版本生成单只股票新闻特征。
     *
     * @param stockCode 股票代码
     * @param featureTradeDate 特征归属交易日
     * @param cutoffTime 信息截止时间
     * @param tradeSignals 已按点时窗口加载的交易信号事件
     * @param preloadedSentimentModelVersion 已批量预加载的情感模型版本，可为空
     * @return 固定结构的新闻事件特征
     * @throws IllegalArgumentException 股票代码、交易日或截止时间无效时抛出
     */
    public NewsEventFeatureVector buildFeature(
            String stockCode, LocalDate featureTradeDate, LocalDateTime cutoffTime,
            List<MarketNewsEventDto> tradeSignals,
            String preloadedSentimentModelVersion) {
        if (stockCode == null || stockCode.isBlank()
                || featureTradeDate == null || cutoffTime == null) {
            throw new IllegalArgumentException("新闻事件特征必须提供股票代码、交易日和截止时间");
        }
        List<MarketNewsEventDto> safeTradeSignals = tradeSignals == null ? List.of() : tradeSignals;
        boolean noEffectiveEvent = safeTradeSignals.isEmpty();
        String sentimentModelVersion = noEffectiveEvent ? null
                : hasText(preloadedSentimentModelVersion)
                ? preloadedSentimentModelVersion : sentimentModelRouterService.activeModelVersion();
        List<SentimentAnalysisResult> modelResults = safeTradeSignals.stream()
                .map(item -> analyzeEvent(item, sentimentModelVersion))
                .toList();
        SentimentAggregateResult aggregate = sentimentAggregationService
                .aggregateEventFacts(safeTradeSignals, cutoffTime, modelResults);
        return NewsEventFeatureVector.builder()
                .stockCode(stockCode.trim())
                .featureTradeDate(featureTradeDate)
                .cutoffTime(cutoffTime)
                .impactDirection(directionValue(aggregate.getImpactDirection()))
                .impactMagnitude(aggregate.getImpactMagnitude())
                .materiality(average(safeTradeSignals, true))
                .novelty(average(safeTradeSignals, false))
                .confidence(aggregate.getConfidence())
                .eventAgeHours(eventAgeHours(safeTradeSignals, cutoffTime))
                .riskFlag(false)
                .eventTypeCounts(eventTypeCounts(safeTradeSignals))
                .noEffectiveEvent(noEffectiveEvent)
                .missingMask(noEffectiveEvent ? 1 : 0)
                .pipelineVersion(resolvePipelineVersion(safeTradeSignals))
                .inputEventCount(safeTradeSignals.size())
                .latestInputPublishTime(latestPublishTime(safeTradeSignals))
                .sentimentModelVersion(sentimentModelVersion)
                .temporalFeatures(buildTemporalFeatures(
                        safeTradeSignals, modelResults, aggregate, cutoffTime, noEffectiveEvent))
                .build();
    }

    /**
     * 批量预加载事件情感结果并写入有界缓存。
     *
     * @param events 同一日期点时窗口内的规范事件
     * @return 本批次使用的情感模型版本；无事件时为空
     */
    public String preloadSentimentResults(List<MarketNewsEventDto> events) {
        if (events == null || events.isEmpty()) {
            return null;
        }
        String sentimentModelVersion = sentimentModelRouterService.activeModelVersion();
        Map<String, MarketNewsEventDto> uniqueEventsByCacheKey = new LinkedHashMap<>();
        Set<String> uniqueCacheKeys = new LinkedHashSet<>();
        for (MarketNewsEventDto event : events) {
            String cacheKey = cacheKey(event, sentimentModelVersion);
            uniqueCacheKeys.add(cacheKey);
            uniqueEventsByCacheKey.putIfAbsent(cacheKey, event);
        }
        sentimentEventCacheRepository.findAllById(uniqueCacheKeys).forEach(document -> {
            if (document != null && document.getId() != null) {
                sentimentResultCache.put(document.getId(), toSentimentResult(document));
            }
        });
        Map<String, MarketNewsEventDto> missingByCacheKey = new LinkedHashMap<>();
        synchronized (sentimentResultCache) {
            for (Map.Entry<String, MarketNewsEventDto> entry : uniqueEventsByCacheKey.entrySet()) {
                String cacheKey = entry.getKey();
                if (!sentimentResultCache.containsKey(cacheKey)) {
                    missingByCacheKey.put(cacheKey, entry.getValue());
                }
            }
        }
        log.info("新闻情感预加载统计: events={}, uniqueEvents={}, cacheHits={}, pendingInference={}",
                events.size(), uniqueCacheKeys.size(),
                uniqueCacheKeys.size() - missingByCacheKey.size(), missingByCacheKey.size());
        if (missingByCacheKey.isEmpty()) {
            return sentimentModelVersion;
        }
        List<String> cacheKeys = new ArrayList<>(missingByCacheKey.keySet());
        List<MarketNewsEventDto> missingEvents = new ArrayList<>(missingByCacheKey.values());
        for (int start = 0; start < cacheKeys.size(); start += PERSISTED_INFERENCE_BATCH_SIZE) {
            int end = Math.min(start + PERSISTED_INFERENCE_BATCH_SIZE, cacheKeys.size());
            List<String> batchCacheKeys = cacheKeys.subList(start, end);
            List<MarketNewsEventDto> batchEvents = missingEvents.subList(start, end);
            List<SentimentModelInput> inputs = batchEvents.stream()
                    .map(event -> sentimentInputAssembler.assemble(
                            event.getNormalizedTitle(), event.getNormalizedContent()))
                    .toList();
            List<SentimentAnalysisResult> results = sentimentModelRouterService
                    .analyzeRequiredBatch(inputs);
            if (results.size() != batchCacheKeys.size()) {
                throw new IllegalStateException("批量情感推理输出数量与事件数量不一致");
            }
            List<SentimentEventCacheDocument> documents = new ArrayList<>(results.size());
            synchronized (sentimentResultCache) {
                for (int index = 0; index < batchCacheKeys.size(); index++) {
                    String cacheKey = batchCacheKeys.get(index);
                    SentimentAnalysisResult result = results.get(index);
                    sentimentResultCache.put(cacheKey, result);
                    documents.add(toCacheDocument(
                            cacheKey, sentimentModelVersion, batchEvents.get(index), result));
                }
            }
            sentimentEventCacheRepository.saveAll(documents);
            log.info("新闻情感持久化批次完成: completed={}, total={}", end, cacheKeys.size());
        }
        return sentimentModelVersion;
    }

    private SentimentAnalysisResult analyzeEvent(
            MarketNewsEventDto event, String sentimentModelVersion) {
        String cacheKey = cacheKey(event, sentimentModelVersion);
        synchronized (sentimentResultCache) {
            SentimentAnalysisResult cached = sentimentResultCache.get(cacheKey);
            if (cached != null) {
                return cached;
            }
        }
        SentimentAnalysisResult analyzed = sentimentModelRouterService.analyzeRequired(
                event.getNormalizedTitle(), event.getNormalizedContent());
        synchronized (sentimentResultCache) {
            SentimentAnalysisResult cached = sentimentResultCache.get(cacheKey);
            if (cached != null) {
                return cached;
            }
            sentimentResultCache.put(cacheKey, analyzed);
            return analyzed;
        }
    }

    private String cacheKey(MarketNewsEventDto event, String sentimentModelVersion) {
        return sentimentModelVersion + "|" + eventIdentity(event);
    }

    private String eventIdentity(MarketNewsEventDto event) {
        return firstText(event.getContentHash(), event.getCanonicalEventId(), event.getId(),
                String.valueOf(Objects.hash(event.getNormalizedTitle(), event.getNormalizedContent())));
    }

    private SentimentEventCacheDocument toCacheDocument(
            String cacheKey,
            String sentimentModelVersion,
            MarketNewsEventDto event,
            SentimentAnalysisResult result) {
        SentimentEventCacheDocument document = new SentimentEventCacheDocument();
        document.setId(cacheKey);
        document.setSentimentModelVersion(sentimentModelVersion);
        document.setEventIdentity(eventIdentity(event));
        document.setLabel(result.getLabel());
        document.setScore(result.getScore());
        document.setConfidence(result.getConfidence());
        document.setCalibratedConfidence(result.getCalibratedConfidence());
        document.setEntropy(result.getEntropy());
        document.setProbabilityMargin(result.getProbabilityMargin());
        document.setAbstained(result.isAbstained());
        document.setChunkCount(result.getChunkCount());
        document.setTextCoverage(result.getTextCoverage());
        document.setNormalizedScore(result.getNormalizedScore());
        document.setProbabilities(result.getProbabilities() == null
                ? Map.of() : Map.copyOf(result.getProbabilities()));
        document.setUpdatedAt(LocalDateTime.now());
        return document;
    }

    private SentimentAnalysisResult toSentimentResult(SentimentEventCacheDocument document) {
        return SentimentAnalysisResult.builder()
                .label(document.getLabel())
                .score(document.getScore())
                .confidence(document.getConfidence())
                .calibratedConfidence(document.getCalibratedConfidence())
                .entropy(document.getEntropy())
                .probabilityMargin(document.getProbabilityMargin())
                .abstained(document.isAbstained())
                .chunkCount(document.getChunkCount())
                .textCoverage(document.getTextCoverage())
                .normalizedScore(document.getNormalizedScore())
                .probabilities(document.getProbabilities() == null
                        ? Map.of() : Map.copyOf(document.getProbabilities()))
                .build();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String firstText(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "UNKNOWN_EVENT";
    }

    private List<Double> buildTemporalFeatures(
            List<MarketNewsEventDto> facts,
            List<SentimentAnalysisResult> results,
            SentimentAggregateResult aggregate,
            LocalDateTime cutoffTime,
            boolean missing) {
        if (missing) {
            return java.util.Collections.nCopies(NewsFeatureSnapshotService.FEATURE_COUNT, 0D);
        }
        List<Double> features = new ArrayList<>(NewsFeatureSnapshotService.FEATURE_COUNT);
        features.add(directionValue(aggregate.getImpactDirection()) * aggregate.getImpactMagnitude());
        features.add(aggregate.getConfidence());
        features.add(average(facts, true));
        features.add(average(facts, false));
        features.add(facts.stream().map(MarketNewsEventDto::getStockSpecificity)
                .filter(Objects::nonNull).mapToDouble(Double::doubleValue).average().orElse(0D));
        features.add(averageTimeDecay(facts, cutoffTime));
        features.add(sourceQuality(facts));
        features.add(Math.min(1D, facts.size() / 5D));
        if (features.size() != NewsFeatureSnapshotService.FEATURE_COUNT) {
            throw new IllegalStateException("temporal-news-v2-8d 特征维度错误: " + features.size());
        }
        return List.copyOf(features);
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
// AI_GENERATE_END---------------
