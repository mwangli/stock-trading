// AI_GENERATE_START-------------
package com.stock.model.service;

import com.stock.market.dto.MarketNewsEventDto;
import com.stock.market.enums.NewsDocumentRole;
import com.stock.market.service.MarketDataQueryService;
import com.stock.model.dto.TrainingSample;
import com.stock.model.enums.SentimentLabelQuality;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 情感分析训练数据预处理服务。
 * 只消费采集层已规范化并按业务角色筛选的事件事实，避免模型域维护第二套清洗规则。
 *
 * @author mwangli
 * @since 2026-03-10
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SentimentDataPreprocessor {

    private static final List<NewsDocumentRole> TRAINING_ROLES = List.of(
            NewsDocumentRole.TRADE_SIGNAL);
    private static final String[] POSITIVE_WORDS = {
        "增长", "盈利", "上涨", "突破", "利好", "推荐", "买入", "收益", "业绩", "向好",
        "创新高", "大涨", "飙升", "激增", "超预期", "优秀", "强劲", "复苏", "回暖"
    };
    private static final String[] NEGATIVE_WORDS = {
        "下跌", "亏损", "风险", "减持", "利空", "卖出", "警告", "业绩下滑", "暴跌",
        "缩水", "下滑", "衰退", "恶化", "承压", "下调", "警惕", "低迷", "疲软", "危机"
    };
    private static final Pattern EXTRA_SPACE_PATTERN = Pattern.compile("\\s+");

    private final MarketDataQueryService marketDataQueryService;

    /**
     * 从规范事件事实中加载情感训练样本。
     *
     * @param numSamples 最大样本数，-1 表示全部
     * @param autoLabel 是否使用规则自动标注
     * @return 按发布时间升序排列的训练样本
     * @throws IllegalStateException 事件事实为空或加载失败时抛出
     */
    public List<TrainingSample> loadTrainingData(int numSamples, boolean autoLabel) {
        log.info("加载事件事实情感训练数据，样本数：{}, 自动标注：{}",
                numSamples == -1 ? "全部" : numSamples, autoLabel);
        try {
            List<MarketNewsEventDto> allFacts = new ArrayList<>(
                    marketDataQueryService.findNewsEvents(TRAINING_ROLES));
            if (allFacts.isEmpty()) {
                throw new IllegalStateException("没有可用的新闻事件事实训练数据");
            }

            Comparator<MarketNewsEventDto> chronologicalOrder = Comparator
                    .comparing(MarketNewsEventDto::getPublishTime,
                            Comparator.nullsLast(Comparator.naturalOrder()))
                    .thenComparing(fact -> fact.getId() == null ? "" : fact.getId());
            Comparator<MarketNewsEventDto> newestFirst = Comparator
                    .comparing(MarketNewsEventDto::getPublishTime,
                            Comparator.nullsLast(Comparator.reverseOrder()))
                    .thenComparing(fact -> fact.getId() == null ? "" : fact.getId());
            allFacts.sort(newestFirst);
            List<MarketNewsEventDto> selectedFacts = new ArrayList<>(
                    numSamples > 0 && numSamples < allFacts.size()
                            ? allFacts.subList(0, numSamples) : allFacts);
            selectedFacts.sort(chronologicalOrder);

            List<TrainingSample> samples = new ArrayList<>();
            for (MarketNewsEventDto fact : selectedFacts) {
                String text = preprocessText(firstNonBlank(fact.getNormalizedTitle(), "")
                        + " " + firstNonBlank(fact.getNormalizedContent(), ""));
                if (text.length() < 10) {
                    continue;
                }
                Integer label = autoLabel ? autoLabelSentiment(text) : null;
                if (label != null) {
                    samples.add(TrainingSample.builder()
                            .text(text)
                            .label(label)
                            .labelQuality(SentimentLabelQuality.WEAK)
                            .labelMethod("RULE_KEYWORD")
                            .labelConfidence(ruleLabelConfidence(text))
                            .source(fact.getStockCode())
                            .sampleId(buildSampleId(fact))
                            .publishedAt(fact.getPublishTime())
                            .build());
                }
            }
            log.info("成功加载 {} 个事件事实训练样本", samples.size());
            return samples;
        } catch (IllegalStateException exception) {
            throw exception;
        } catch (Exception exception) {
            log.error("加载事件事实训练数据失败", exception);
            throw new IllegalStateException("加载事件事实训练数据失败", exception);
        }
    }

    private String buildSampleId(MarketNewsEventDto fact) {
        String stockCode = firstNonBlank(fact.getStockCode(), "unknown");
        String eventId = firstNonBlank(fact.getCanonicalEventId(), fact.getId());
        return stockCode + ":" + firstNonBlank(eventId, "unknown");
    }

    /**
     * 使用关键词规则自动标注情感。
     *
     * @param text 已由采集层规范化的事件文本
     * @return 情感标签：0 中性、1 正面、2 负面
     */
    public Integer autoLabelSentiment(String text) {
        int positiveCount = 0;
        int negativeCount = 0;
        for (String word : POSITIVE_WORDS) {
            if (text.contains(word)) {
                positiveCount++;
            }
        }
        for (String word : NEGATIVE_WORDS) {
            if (text.contains(word)) {
                negativeCount++;
            }
        }
        if (positiveCount > negativeCount + 1) {
            return 1;
        }
        if (negativeCount > positiveCount + 1) {
            return 2;
        }
        return 0;
    }

    private double ruleLabelConfidence(String text) {
        int positiveCount = 0;
        int negativeCount = 0;
        for (String word : POSITIVE_WORDS) {
            if (text.contains(word)) {
                positiveCount++;
            }
        }
        for (String word : NEGATIVE_WORDS) {
            if (text.contains(word)) {
                negativeCount++;
            }
        }
        int evidenceCount = positiveCount + negativeCount;
        if (evidenceCount == 0) {
            return 0.34D;
        }
        return Math.min(0.70D, 0.40D + Math.abs(positiveCount - negativeCount) * 0.10D);
    }
    /**
     * 对事件事实文本执行模型输入所需的轻量字符约束。
     * 采集层已经处理 HTML、URL、模板和重复前缀，本方法不再承担业务清洗。
     *
     * @param text 采集层规范化文本
     * @return 模型输入文本
     */
    public String preprocessText(String text) {
        if (text == null || text.trim().isEmpty()) {
            return "";
        }
        String cleaned = text.toLowerCase()
                .replaceAll("[^\\u4e00-\\u9fa5a-zA-Z0-9，。！？、；：\"'（）《》【】…—]", " ");
        return EXTRA_SPACE_PATTERN.matcher(cleaned).replaceAll(" ").trim();
    }

    private String firstNonBlank(String preferred, String fallback) {
        return preferred == null || preferred.isBlank() ? fallback : preferred;
    }
}
// AI_GENERATE_END-------------
