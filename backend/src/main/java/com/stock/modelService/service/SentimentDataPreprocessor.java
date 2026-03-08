package com.stock.modelService.service;

import com.stock.dataCollector.entity.StockNews;
import com.stock.dataCollector.repository.NewsRepository;
import com.stock.modelService.config.SentimentTrainingConfig;
import com.stock.modelService.dataset.NewsSentimentDataset;
import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer;
import com.stock.modelService.dto.TrainingSample;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 情感分析训练数据预处理服务
 * 负责文本清洗、自动标注、数据集构建
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SentimentDataPreprocessor {

    private final NewsRepository newsRepository;
    private final SentimentTrainingConfig config;

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
     * 从新闻数据加载并准备训练样本
     */
    public List<TrainingSample> loadTrainingData(int numSamples, boolean autoLabel) {
        log.info("加载情感训练数据，样本数：{}, 自动标注：{}", 
                numSamples == -1 ? "全部" : numSamples, autoLabel);

        try {
            // 1. 从 MongoDB 获取新闻数据
            List<StockNews> allNews = newsRepository.findAll();
            
            if (allNews.isEmpty()) {
                log.warn("没有找到新闻数据，生成模拟数据");
                return generateSyntheticData(numSamples);
            }

            // 2. 限制样本数
            List<StockNews> newsList = numSamples > 0 && numSamples < allNews.size()
                    ? allNews.subList(0, numSamples)
                    : allNews;

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
                            .build());
                }
            }

            log.info("成功加载 {} 个训练样本", samples.size());
            return samples;

        } catch (Exception e) {
            log.error("加载训练数据失败", e);
            return generateSyntheticData(numSamples > 0 ? numSamples : 100);
        }
    }

    /**
     * 自动标注情感（基于规则）
     * 0 = negative, 1 = neutral, 2 = positive
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
     * 文本预处理
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

    /**
     * 生成模拟训练数据（用于测试）
     */
    public List<TrainingSample> generateSyntheticData(int count) {
        log.info("生成 {} 个模拟训练数据", count > 0 ? count : 100);
        
        List<TrainingSample> samples = new ArrayList<>();
        
        // 正面样本
        String[] positiveTexts = {
            "公司业绩大幅增长，净利润再创新高",
            "股票价格持续上涨，突破历史新高",
            "年度报告显示盈利超预期，投资者信心增强",
            "新产品发布市场反应热烈，订单激增",
            "行业利好政策出台，公司发展前景向好"
        };

        // 负面样本
        String[] negativeTexts = {
            "公司业绩下滑，亏损严重",
            "股票价格暴跌，市值缩水",
            "收到监管警告，面临处罚风险",
            "大股东减持，市场信心受挫",
            "行业低迷，公司经营承压"
        };

        // 中性样本
        String[] neutralTexts = {
            "公司发布日常经营公告",
            "股票价格波动不大，成交量平稳",
            "行业保持稳定发展态势",
            "公司召开董事会会议",
            "市场整体表现平淡"
        };

        int n = count > 0 ? count : 100;
        for (int i = 0; i < n; i++) {
            if (i % 3 == 0) {
                samples.add(TrainingSample.builder()
                        .text(positiveTexts[i % positiveTexts.length] + " " + i)
                        .label(1) // positive
                        .source("synthetic")
                        .build());
            } else if (i % 3 == 1) {
                samples.add(TrainingSample.builder()
                        .text(negativeTexts[i % negativeTexts.length] + " " + i)
                        .label(2) // negative
                        .source("synthetic")
                        .build());
            } else {
                samples.add(TrainingSample.builder()
                        .text(neutralTexts[i % neutralTexts.length] + " " + i)
                        .label(0) // neutral
                        .source("synthetic")
                        .build());
            }
        }

        return samples;
    }

    /**
     * 划分训练集和验证集
     */
    public DatasetSplit splitDataset(List<TrainingSample> samples) {
        int trainSize = (int) (samples.size() * config.getTrainRatio());
        int valSize = samples.size() - trainSize;

        // 随机打乱
        Collections.shuffle(samples);

        List<TrainingSample> trainData = samples.subList(0, trainSize);
        List<TrainingSample> valData = samples.subList(trainSize, trainSize + valSize);

        log.info("数据集划分：训练集={}, 验证集={}", trainData.size(), valData.size());

        return new DatasetSplit(trainData, valData);
    }

    /**
     * 基于训练样本和 tokenizer 构建 DJL 数据集
     */
    public NewsSentimentDataset buildDataset(List<TrainingSample> samples, HuggingFaceTokenizer tokenizer) throws java.io.IOException {
        return NewsSentimentDataset.builder()
                .setSamples(samples)
                .setTokenizer(tokenizer)
                .setMaxLength(config.getMaxSequenceLength())
                .setSampling(config.getBatchSize(), true)
                .build();
    }

    /**
     * 数据集划分结果
     */
    @lombok.Data
    @lombok.RequiredArgsConstructor
    public static class DatasetSplit {
        private final List<TrainingSample> trainData;
        private final List<TrainingSample> valData;
    }
}
