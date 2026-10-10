// AI_GENERATE_START -----
package com.stock.modelService.service;

import com.stock.dataCollector.domain.entity.StockNews;
import com.stock.dataCollector.persistence.NewsRepository;
import com.stock.modelService.domain.dto.TrainingSample;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 情感分析训练数据预处理服务
 * 负责真实新闻文本清洗、自动标注和数据集构建。
 *
 * @author mwangli
 * @since 2026-03-10
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SentimentDataPreprocessor {

    private final NewsRepository newsRepository;
    // 情感关键词（用于自动标注）
    private static final String[] POSITIVE_WORDS = {
        "增长", "盈利", "上涨", "突破", "利好", "推荐", "买入", "收益", "业绩", "向好",
        "创新高", "大涨", "飙升", "激增", "超预期", "优秀", "强劲", "复苏", "回暖"
    };

    private static final String[] NEGATIVE_WORDS = {
        "下跌", "亏损", "风险", "减持", "利空", "卖出", "警告", "业绩下滑", "暴跌",
        "缩水", "下滑", "衰退", "恶化", "承压", "下调", "警惕", "低迷", "疲软", "危机"
    };

    private static final Pattern HTML_PATTERN = Pattern.compile("<[^>]+>");
    private static final Pattern URL_PATTERN = Pattern.compile("https?://\\S+");
    private static final Pattern EXTRA_SPACE_PATTERN = Pattern.compile("\\s+");

    /**
     * 从 MongoDB 中加载真实新闻并准备训练样本。
     *
     * @param numSamples 最大样本数，-1 表示全部
     * @param autoLabel 是否使用规则自动标注
     * @return 真实新闻训练样本
     * @throws IllegalStateException 新闻数据为空或加载失败时抛出
     */
    public List<TrainingSample> loadTrainingData(int numSamples, boolean autoLabel) {
        log.info("加载情感训练数据，样本数：{}, 自动标注：{}", 
                numSamples == -1 ? "全部" : numSamples, autoLabel);

        try {
            // 1. 从 MongoDB 获取新闻数据
            List<StockNews> allNews = new ArrayList<>(newsRepository.findAll());
            
            if (allNews.isEmpty()) {
                throw new IllegalStateException("没有可用的真实新闻训练数据");
            }

            // 2. 先选最近 N 条，再恢复时间升序，兼顾数据新鲜度和可重复时间切分。
            Comparator<StockNews> chronologicalOrder = Comparator
                    .comparing(StockNews::getPublishTime,
                            Comparator.nullsLast(Comparator.naturalOrder()))
                    .thenComparing(news -> news.getId() == null ? "" : news.getId());
            Comparator<StockNews> newestFirst = Comparator
                    .comparing(StockNews::getPublishTime,
                            Comparator.nullsLast(Comparator.reverseOrder()))
                    .thenComparing(news -> news.getId() == null ? "" : news.getId());
            allNews.sort(newestFirst);
            List<StockNews> newsList = new ArrayList<>(
                    numSamples > 0 && numSamples < allNews.size()
                            ? allNews.subList(0, numSamples)
                            : allNews);
            newsList.sort(chronologicalOrder);

            // 3. 预处理和标注
            List<TrainingSample> samples = new ArrayList<>();
            for (StockNews news : newsList) {
                String text = preprocessText(news.getTitle() + " " + news.getContent());
                if (text.length() < 10) {
                    continue; // 跳过太短的文本
                }

                Integer label = autoLabel ? autoLabelSentiment(text) : null;
                if (label != null) {
                    samples.add(TrainingSample.builder()
                            .text(text)
                            .label(label)
                            .source(news.getStockCode())
                            .sampleId(buildSampleId(news))
                            .publishedAt(news.getPublishTime() != null
                                    ? news.getPublishTime() : news.getCreateTime())
                            .build());
                }
            }

            log.info("成功加载 {} 个训练样本", samples.size());
            return samples;

        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            log.error("加载训练数据失败", e);
            throw new IllegalStateException("加载真实新闻训练数据失败", e);
        }
    }

    private String buildSampleId(StockNews news) {
        String stockCode = news.getStockCode() == null ? "unknown" : news.getStockCode();
        String newsId = news.getExternalId() != null && !news.getExternalId().isBlank()
                ? news.getExternalId() : news.getId();
        return stockCode + ":" + (newsId == null ? "unknown" : newsId);
    }

    /**
     * 使用关键词规则自动标注情感。
     *
     * @param text 已清洗的新闻文本
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
            return 1; // positive (was 2)
        } else if (negativeCount > positiveCount + 1) {
            return 2; // negative (was 0)
        } else {
            return 0; // neutral (was 1)
        }
    }

    /**
     * 清洗新闻文本中的 HTML、URL 和无关字符。
     *
     * @param text 原始新闻文本
     * @return 清洗后的文本
     */
    public String preprocessText(String text) {
        if (text == null || text.trim().isEmpty()) {
            return "";
        }

        // 1. 转小写
        String cleaned = text.toLowerCase();

        // 2. 移除 HTML 标签
        cleaned = HTML_PATTERN.matcher(cleaned).replaceAll("");

        // 3. 移除 URL
        cleaned = URL_PATTERN.matcher(cleaned).replaceAll("");

        // 4. 移除特殊字符（保留中文和标点）
        cleaned = cleaned.replaceAll("[^\\u4e00-\\u9fa5a-zA-Z0-9，。！？、；：\"'（）《》【】…—]", " ");

        // 5. 压缩多余空格
        cleaned = EXTRA_SPACE_PATTERN.matcher(cleaned).replaceAll(" ").trim();

        return cleaned;
    }

}
// AI_GENERATE_END -----
