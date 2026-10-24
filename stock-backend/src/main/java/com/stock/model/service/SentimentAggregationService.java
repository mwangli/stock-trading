// AI_GENERATE_START------------------
package com.stock.model.service;

import com.stock.market.dto.MarketNewsEventDto;
import com.stock.market.enums.NewsDocumentType;
import com.stock.market.service.NewsEventClassifier;
import com.stock.model.vo.SentimentAggregateResult;
import com.stock.model.vo.SentimentAnalysisResult;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 股票新闻事件情感聚合服务。
 * 对新闻和公告进行事件级去重，并根据时间、来源、事件类型和模型置信度计算交易情感信号。
 *
 * @author mwangli
 * @since 2026-10-08
 */
@Service
@RequiredArgsConstructor
public class SentimentAggregationService {

    private static final int MAX_TEXT_LENGTH = 2000;
    private static final double LOG_TWO = Math.log(2D);
    private static final double STRONG_NEGATIVE_SCORE_THRESHOLD = -0.3D;
    private static final int NEGATION_SCOPE_LENGTH = 320;
    private static final List<String> STRONG_NEGATIVE_KEYWORDS = List.of(
            "退市", "财务造假", "立案调查", "重大违法", "暂停上市", "终止上市",
            "债务违约", "破产重整", "实控人被捕", "资金占用", "审计无法表示意见");
    private static final List<String> DIRECT_NEGATION_MARKERS = List.of(
            "不存在", "不涉及", "未发生", "未出现", "未受到", "未被", "没有", "不属于", "不构成");
    private static final List<String> LIST_NEGATION_MARKERS = List.of(
            "无下列", "无以下", "不存在下列", "不存在以下", "没有下列", "没有以下", "不具有下列");
    private static final List<String> NEGATION_REVERSAL_MARKERS = List.of(
            "但是", "但其", "然而", "本次", "近日收到", "现收到", "实际发生");

    private final SentimentModelRouterService sentimentModelRouterService;
    private final NewsEventClassifier newsEventClassifier;

    /**
     * 分析单条文本并生成与选股聚合一致的业务判断。
     *
     * @param text 待分析文本
     * @param category 来源分类，例如新闻或公告
     * @param modelResult 已完成的情感模型结果，避免重复推理
     * @return 单条文本的业务影响结果
     */
    public SentimentAggregateResult assessText(String text, String category,
                                               SentimentAnalysisResult modelResult) {
        AggregationNews news = new AggregationNews();
        news.setTitle(extractTitle(text));
        news.setContent(text);
        news.setCategory(category);
        news.setPublishTime(LocalDateTime.now());
        String eventType = resolveEventType(news);
        double coverage = 0.5D + 1D / 6D;
        double confidence = clamp(modelResult.getConfidence() * coverage, 0D, 1D);
        double impactMagnitude = clamp(normalizedPriority(news) * coverage, 0D, 1D);
        double newsImpactScore = clamp(modelResult.getScore() * impactMagnitude * confidence, -1D, 1D);
        boolean strongNegative = isStrongNegative(news, modelResult);
        if (strongNegative) {
            newsImpactScore = Math.min(newsImpactScore, -0.8D);
        }
        boolean actionable = strongNegative || (Math.abs(newsImpactScore) >= 0.12D && confidence >= 0.55D);
        return SentimentAggregateResult.builder()
                .newsImpactScore(newsImpactScore)
                .impactMagnitude(impactMagnitude)
                .confidence(confidence)
                .newsCount(1)
                .uniqueEventCount(1)
                .strongNegative(strongNegative)
                .dominantEventType(eventType)
                .effectiveHorizonHours(resolveEffectiveHorizonHours(eventType))
                .actionable(actionable)
                .impactDirection(resolveImpactDirection(newsImpactScore))
                .strategyAction(resolveStrategyAction(newsImpactScore, strongNegative, actionable))
                .build();
    }

    /**
     * 聚合一只股票指定时间窗口内的新闻和公告情感。
     *
     * @param newsItems 新闻和公告列表
     * @param evaluationTime 计算时点
     * @return 去重和加权后的情感结果
     */
    private SentimentAggregateResult aggregate(List<AggregationNews> newsItems, LocalDateTime evaluationTime) {
        if (newsItems == null || newsItems.isEmpty()) {
            return neutralResult();
        }
        LocalDateTime now = evaluationTime == null ? LocalDateTime.now() : evaluationTime;
        Map<String, List<AggregationNews>> eventGroups = groupByEvent(newsItems);
        double weightedScore = 0D;
        double totalWeight = 0D;
        double weightedConfidence = 0D;
        double weightedPriority = 0D;
        Map<String, Double> eventTypeWeights = new LinkedHashMap<>();
        boolean strongNegative = false;
        String strongNegativeTitle = null;

        for (List<AggregationNews> group : eventGroups.values()) {
            AggregationNews primary = group.get(0);
            String title = firstNonBlank(primary.getNormalizedTitle(), primary.getTitle());
            String content = firstNonBlank(primary.getNormalizedContent(), primary.getContent());
            if (title.isBlank() && content.isBlank()) {
                continue;
            }
            SentimentAnalysisResult modelResult = sentimentModelRouterService.analyzeRequired(title, content);
            double weight = calculateWeight(primary, group.size(), now, modelResult.getConfidence());
            weightedScore += modelResult.getScore() * weight;
            weightedConfidence += modelResult.getConfidence() * weight;
            weightedPriority += normalizedPriority(primary) * weight;
            eventTypeWeights.merge(resolveEventType(primary), weight, Double::sum);
            totalWeight += weight;

            if (isStrongNegative(primary, modelResult)) {
                strongNegative = true;
                if (strongNegativeTitle == null) {
                    strongNegativeTitle = primary.getTitle();
                }
            }
        }

        if (totalWeight <= 0D) {
            return neutralResult();
        }
        double coverage = Math.min(1D, 0.5D + eventGroups.size() / 6D);
        double confidence = clamp(weightedConfidence / totalWeight * coverage, 0D, 1D);
        double polarityScore = clamp(weightedScore / totalWeight, -1D, 1D);
        double impactMagnitude = clamp(weightedPriority / totalWeight * coverage, 0D, 1D);
        double newsImpactScore = clamp(polarityScore * impactMagnitude * confidence, -1D, 1D);
        if (strongNegative) {
            newsImpactScore = Math.min(newsImpactScore, -0.8D);
        }
        String dominantEventType = eventTypeWeights.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);
        int effectiveHorizonHours = resolveEffectiveHorizonHours(dominantEventType);
        String impactDirection = resolveImpactDirection(newsImpactScore);
        String strategyAction = resolveStrategyAction(newsImpactScore, strongNegative,
                Math.abs(newsImpactScore) >= 0.12D && confidence >= 0.55D);
        return SentimentAggregateResult.builder()
                .newsImpactScore(newsImpactScore)
                .impactMagnitude(impactMagnitude)
                .confidence(confidence)
                .newsCount(newsItems.size())
                .uniqueEventCount(eventGroups.size())
                .strongNegative(strongNegative)
                .strongNegativeTitle(strongNegativeTitle)
                .dominantEventType(dominantEventType)
                .effectiveHorizonHours(effectiveHorizonHours)
                .actionable(strongNegative || (Math.abs(newsImpactScore) >= 0.12D && confidence >= 0.55D))
                .impactDirection(impactDirection)
                .strategyAction(strategyAction)
                .build();
    }

    /**
     * 聚合采集层生成的规范事件事实。
     *
     * @param eventFacts 点时有效且已按业务角色过滤的事件事实
     * @param evaluationTime 计算时点
     * @return 去重和加权后的情感结果
     */
    public SentimentAggregateResult aggregateEventFacts(
            List<MarketNewsEventDto> eventFacts, LocalDateTime evaluationTime) {
        if (eventFacts == null || eventFacts.isEmpty()) {
            return neutralResult();
        }
        List<AggregationNews> newsItems = eventFacts.stream().map(this::toAggregationInput).toList();
        return aggregate(newsItems, evaluationTime);
    }

    private AggregationNews toAggregationInput(MarketNewsEventDto fact) {
        AggregationNews news = new AggregationNews();
        news.setExternalId(fact.getSourceExternalIds() == null ? null
                : fact.getSourceExternalIds().stream().findFirst().orElse(null));
        news.setStockCode(fact.getStockCode());
        news.setStockName(fact.getStockName());
        news.setTitle(fact.getNormalizedTitle());
        news.setContent(fact.getNormalizedContent());
        news.setNormalizedTitle(fact.getNormalizedTitle());
        news.setNormalizedContent(fact.getNormalizedContent());
        news.setDocumentType(fact.getDocumentType());
        news.setDocumentRole(fact.getDocumentRole());
        news.setEventType(fact.getEventType());
        news.setCanonicalEventId(fact.getCanonicalEventId());
        news.setContentHash(fact.getContentHash());
        news.setInformationPriority(fact.getInformationPriority());
        news.setNoveltyScore(fact.getNoveltyScore());
        news.setMaterialityScore(fact.getMaterialityScore());
        news.setStockSpecificity(fact.getStockSpecificity());
        news.setSource(fact.getSources() == null ? null
                : fact.getSources().stream().findFirst().orElse(null));
        news.setPublishTime(fact.getPublishTime());
        return news;
    }

    private Map<String, List<AggregationNews>> groupByEvent(List<AggregationNews> newsItems) {
        Map<String, List<AggregationNews>> groups = new LinkedHashMap<>();
        for (AggregationNews news : newsItems) {
            String fingerprint = buildFingerprint(news);
            groups.computeIfAbsent(fingerprint, ignored -> new ArrayList<>()).add(news);
        }
        return groups;
    }

    private String buildFingerprint(AggregationNews news) {
        if (news.getCanonicalEventId() != null && !news.getCanonicalEventId().isBlank()) {
            return news.getCanonicalEventId();
        }
        if (news.getContentHash() != null && !news.getContentHash().isBlank()) {
            return news.getContentHash();
        }
        String title = normalize(news.getTitle());
        if (!title.isBlank()) {
            return title.length() <= 80 ? title : title.substring(0, 80);
        }
        String content = normalize(news.getContent());
        if (!content.isBlank()) {
            return content.length() <= 120 ? content : content.substring(0, 120);
        }
        return news.getExternalId() == null ? "unknown-event" : news.getExternalId();
    }

    private String normalize(String text) {
        if (text == null) {
            return "";
        }
        return text.toLowerCase(Locale.ROOT)
                .replaceAll("[\\p{Punct}\\p{IsPunctuation}\\s]+", "")
                .trim();
    }

    private double calculateWeight(AggregationNews news, int duplicateCount,
                                   LocalDateTime now, double modelConfidence) {
        long ageMinutes = news.getPublishTime() == null
                ? 0L
                : Math.max(0L, Duration.between(news.getPublishTime(), now).toMinutes());
        double ageHours = ageMinutes / 60D;
        double halfLifeHours = isAnnouncement(news) ? 24D : 12D;
        double timeDecay = Math.exp(-LOG_TWO * ageHours / halfLifeHours);
        double duplicatePenalty = 1D / Math.sqrt(Math.max(1, duplicateCount));
        return clamp(modelConfidence, 0D, 1D)
                * timeDecay
                * sourceWeight(news.getSource())
                * eventTypeWeight(news)
                * duplicatePenalty
                * optionalQualityWeight(news.getNoveltyScore())
                * optionalQualityWeight(news.getStockSpecificity());
    }

    private double optionalQualityWeight(Double value) {
        return value == null ? 1D : clamp(value, 0D, 1D);
    }

    private double normalizedPriority(AggregationNews news) {
        int priority = news.getInformationPriority() == null
                ? newsEventClassifier.priority(resolveEventType(news)) : news.getInformationPriority();
        return clamp(priority / 5D, 0D, 1D);
    }

    private String resolveEventType(AggregationNews news) {
        return news.getEventType() == null || news.getEventType().isBlank()
                ? newsEventClassifier.classify(news.getTitle(), news.getContent(), news.getCategory())
                : news.getEventType();
    }

    private int resolveEffectiveHorizonHours(String eventType) {
        return switch (eventType) {
            case "REGULATORY_OR_LITIGATION" -> 120;
            case "EARNINGS" -> 72;
            case "CAPITAL_RISK" -> 72;
            case "SHAREHOLDER_OR_EXECUTIVE", "MATERIAL_BUSINESS" -> 48;
            default -> 24;
        };
    }

    private double sourceWeight(String source) {
        String normalized = normalize(source);
        if (containsAny(normalized, "上海证券交易所", "深圳证券交易所", "北京证券交易所", "证监会")) {
            return 1.4D;
        }
        if (containsAny(normalized, "公司公告", "上市公司")) {
            return 1.3D;
        }
        if (containsAny(normalized, "证券时报", "中国证券报", "上海证券报", "财联社")) {
            return 1.15D;
        }
        return 1D;
    }

    private double eventTypeWeight(AggregationNews news) {
        return switch (resolveEventType(news)) {
            case "REGULATORY_OR_LITIGATION", "EARNINGS" -> 1.45D;
            case "SHAREHOLDER_OR_EXECUTIVE", "MATERIAL_BUSINESS" -> 1.30D;
            case "CAPITAL_RISK" -> 1.25D;
            default -> isAnnouncement(news) ? 1.15D : 1D;
        };
    }

    private boolean isStrongNegative(AggregationNews news, SentimentAnalysisResult result) {
        if (result == null || result.getScore() > STRONG_NEGATIVE_SCORE_THRESHOLD) {
            return false;
        }
        String text = (news.getTitle() == null ? "" : news.getTitle())
                + (news.getContent() == null ? "" : news.getContent());
        return STRONG_NEGATIVE_KEYWORDS.stream().anyMatch(keyword -> containsAffirmedRisk(text, keyword));
    }

    private boolean containsAffirmedRisk(String text, String keyword) {
        int searchFrom = 0;
        while (searchFrom < text.length()) {
            int keywordIndex = text.indexOf(keyword, searchFrom);
            if (keywordIndex < 0) {
                return false;
            }
            if (!isNegatedRiskContext(text, keywordIndex)) {
                return true;
            }
            searchFrom = keywordIndex + keyword.length();
        }
        return false;
    }

    private boolean isNegatedRiskContext(String text, int keywordIndex) {
        String directContext = text.substring(Math.max(0, keywordIndex - 40), keywordIndex);
        boolean directlyNegated = DIRECT_NEGATION_MARKERS.stream().anyMatch(directContext::contains);
        boolean directReversal = NEGATION_REVERSAL_MARKERS.stream().anyMatch(directContext::contains);
        if (directlyNegated && !directReversal) {
            return true;
        }

        String scopeContext = text.substring(Math.max(0, keywordIndex - NEGATION_SCOPE_LENGTH), keywordIndex);
        int markerIndex = LIST_NEGATION_MARKERS.stream()
                .mapToInt(scopeContext::lastIndexOf)
                .max()
                .orElse(-1);
        if (markerIndex < 0) {
            return false;
        }
        String scopedText = scopeContext.substring(markerIndex);
        return NEGATION_REVERSAL_MARKERS.stream().noneMatch(scopedText::contains);
    }

    private String extractTitle(String text) {
        if (text == null) {
            return null;
        }
        int lineBreakIndex = text.indexOf('\n');
        if (lineBreakIndex <= 0) {
            return null;
        }
        String firstLine = text.substring(0, lineBreakIndex).trim();
        if (firstLine.isEmpty()) {
            return null;
        }
        return firstLine.length() <= 200 ? firstLine : firstLine.substring(0, 200);
    }

    private boolean isAnnouncement(AggregationNews news) {
        if (news.getDocumentType() != null) {
            return news.getDocumentType() == NewsDocumentType.OFFICIAL_DISCLOSURE;
        }
        String category = news.getCategory();
        return category != null && category.contains("公告");
    }

    private boolean containsAny(String text, String... values) {
        for (String value : values) {
            if (text.contains(normalize(value))) {
                return true;
            }
        }
        return false;
    }

    private String firstNonBlank(String preferred, String fallback) {
        if (preferred != null && !preferred.isBlank()) {
            return preferred.trim();
        }
        return fallback == null ? "" : fallback.trim();
    }

    private SentimentAggregateResult neutralResult() {
        return SentimentAggregateResult.builder()
                .newsImpactScore(0D)
                .impactMagnitude(0D)
                .confidence(0D)
                .newsCount(0)
                .uniqueEventCount(0)
                .strongNegative(false)
                .actionable(false)
                .effectiveHorizonHours(0)
                .impactDirection("NEUTRAL")
                .strategyAction("NO_ACTION")
                .build();
    }

    private String resolveImpactDirection(double newsImpactScore) {
        if (newsImpactScore >= 0.12D) {
            return "BULLISH";
        }
        if (newsImpactScore <= -0.12D) {
            return "BEARISH";
        }
        return "NEUTRAL";
    }

    private String resolveStrategyAction(double newsImpactScore, boolean strongNegative, boolean actionable) {
        if (strongNegative) {
            return "RISK_BLOCK";
        }
        if (!actionable) {
            return "NO_ACTION";
        }
        return newsImpactScore > 0D ? "ENHANCE" : "REDUCE";
    }

    private double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    /**
     * 模型包内部使用的新闻聚合输入，不暴露市场持久化对象。
     *
     * @author mwangli
     * @since 2026-10-24
     */
    @Data
    private static class AggregationNews {
        private String externalId;
        private String title;
        private String content;
        private String stockCode;
        private String stockName;
        private String source;
        private String category;
        private String normalizedTitle;
        private String normalizedContent;
        private NewsDocumentType documentType;
        private com.stock.market.enums.NewsDocumentRole documentRole;
        private String eventType;
        private String contentHash;
        private String canonicalEventId;
        private Integer informationPriority;
        private Double noveltyScore;
        private Double materialityScore;
        private Double stockSpecificity;
        private LocalDateTime publishTime;
    }
}
// AI_GENERATE_END------------------
