// AI_GENERATE_START ---
package com.stock.modelService.service;

import com.stock.dataCollector.domain.entity.StockNews;
import com.stock.modelService.domain.vo.SentimentAggregateResult;
import com.stock.modelService.domain.vo.SentimentAnalysisResult;
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
    private static final List<String> STRONG_NEGATIVE_KEYWORDS = List.of(
            "退市", "财务造假", "立案调查", "重大违法", "暂停上市", "终止上市",
            "债务违约", "破产重整", "实控人被捕", "资金占用", "审计无法表示意见");

    private final SentimentInferenceService sentimentInferenceService;

    /**
     * 聚合一只股票指定时间窗口内的新闻和公告情感。
     *
     * @param newsItems 新闻和公告列表
     * @param evaluationTime 计算时点
     * @return 去重和加权后的情感结果
     */
    public SentimentAggregateResult aggregate(List<StockNews> newsItems, LocalDateTime evaluationTime) {
        if (newsItems == null || newsItems.isEmpty()) {
            return neutralResult();
        }
        LocalDateTime now = evaluationTime == null ? LocalDateTime.now() : evaluationTime;
        Map<String, List<StockNews>> eventGroups = groupByEvent(newsItems);
        double weightedScore = 0D;
        double totalWeight = 0D;
        double weightedConfidence = 0D;
        boolean strongNegative = false;
        String strongNegativeTitle = null;

        for (List<StockNews> group : eventGroups.values()) {
            StockNews primary = group.get(0);
            String text = buildText(primary);
            if (text.isBlank()) {
                continue;
            }
            SentimentAnalysisResult modelResult = sentimentInferenceService.analyzeSentimentRequired(text);
            double weight = calculateWeight(primary, group.size(), now, modelResult.getConfidence());
            weightedScore += modelResult.getScore() * weight;
            weightedConfidence += modelResult.getConfidence() * weight;
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
        double coverage = Math.min(1D, eventGroups.size() / 5D);
        double confidence = clamp(weightedConfidence / totalWeight * coverage, 0D, 1D);
        double score = clamp(weightedScore / totalWeight, -1D, 1D);
        if (strongNegative) {
            score = Math.min(score, -0.8D);
        }
        return SentimentAggregateResult.builder()
                .score(score)
                .confidence(confidence)
                .newsCount(newsItems.size())
                .uniqueEventCount(eventGroups.size())
                .strongNegative(strongNegative)
                .strongNegativeTitle(strongNegativeTitle)
                .build();
    }

    private Map<String, List<StockNews>> groupByEvent(List<StockNews> newsItems) {
        Map<String, List<StockNews>> groups = new LinkedHashMap<>();
        for (StockNews news : newsItems) {
            String fingerprint = buildFingerprint(news);
            groups.computeIfAbsent(fingerprint, ignored -> new ArrayList<>()).add(news);
        }
        return groups;
    }

    private String buildFingerprint(StockNews news) {
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

    private double calculateWeight(StockNews news, int duplicateCount,
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
                * duplicatePenalty;
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

    private double eventTypeWeight(StockNews news) {
        String text = normalize((news.getTitle() == null ? "" : news.getTitle())
                + (news.getContent() == null ? "" : news.getContent()));
        if (containsAny(text, "业绩预告", "业绩快报", "回购", "增持", "减持", "立案调查", "处罚", "退市")) {
            return 1.3D;
        }
        if (isAnnouncement(news)) {
            return 1.15D;
        }
        return 1D;
    }

    private boolean isStrongNegative(StockNews news, SentimentAnalysisResult result) {
        String text = (news.getTitle() == null ? "" : news.getTitle())
                + (news.getContent() == null ? "" : news.getContent());
        return result.getScore() < 0.3D && STRONG_NEGATIVE_KEYWORDS.stream().anyMatch(text::contains);
    }

    private boolean isAnnouncement(StockNews news) {
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

    private String buildText(StockNews news) {
        String title = news.getTitle() == null ? "" : news.getTitle().trim();
        String content = news.getContent() == null ? "" : news.getContent().trim();
        String text = (title + "。" + content).trim();
        return text.length() <= MAX_TEXT_LENGTH ? text : text.substring(0, MAX_TEXT_LENGTH);
    }

    private SentimentAggregateResult neutralResult() {
        return SentimentAggregateResult.builder()
                .score(0D)
                .confidence(0D)
                .newsCount(0)
                .uniqueEventCount(0)
                .strongNegative(false)
                .build();
    }

    private double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
// AI_GENERATE_END ---
