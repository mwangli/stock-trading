package com.stock.models.service;

import com.stock.models.config.SentimentTrainingConfig;
import com.stock.models.dto.TrainingSample;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 情感分析数据预处理服务测试
 */
@ExtendWith(MockitoExtension.class)
class SentimentDataPreprocessorTest {

    @Mock
    private com.stock.databus.repository.NewsRepository newsRepository;

    @Mock
    private SentimentTrainingConfig config;

    @InjectMocks
    private SentimentDataPreprocessor preprocessor;

    @BeforeEach
    void setUp() {
        // 配置默认参数
        org.mockito.Mockito.when(config.getTrainRatio()).thenReturn(0.8);
        org.mockito.Mockito.when(config.getNumLabels()).thenReturn(3);
        org.mockito.Mockito.when(config.getLabels()).thenReturn(new String[]{"negative", "neutral", "positive"});
    }

    @Test
    void testPreprocessText() {
        String rawText = "<p>公司业绩<a href='http://example.com'>大幅增长</a>！！！</p>";
        String cleaned = preprocessor.preprocessText(rawText);

        assertNotNull(cleaned);
        assertFalse(cleaned.contains("<"));
        assertFalse(cleaned.contains("http"));
        assertTrue(cleaned.contains("公司业绩"));
        assertTrue(cleaned.contains("增长"));
    }

    @Test
    void testAutoLabelPositive() {
        String text = "公司业绩大幅增长，净利润创新高，股票价格持续上涨";
        Integer label = preprocessor.autoLabelSentiment(text);

        assertNotNull(label);
        assertEquals(2, label); // positive
    }

    @Test
    void testAutoLabelNegative() {
        String text = "公司业绩下滑，亏损严重，股票价格暴跌";
        Integer label = preprocessor.autoLabelSentiment(text);

        assertNotNull(label);
        assertEquals(0, label); // negative
    }

    @Test
    void testAutoLabelNeutral() {
        String text = "公司发布日常经营公告，股票价格波动不大";
        Integer label = preprocessor.autoLabelSentiment(text);

        assertNotNull(label);
        assertEquals(1, label); // neutral
    }

    @Test
    void testGenerateSyntheticData() {
        List<TrainingSample> samples = preprocessor.generateSyntheticData(30);

        assertNotNull(samples);
        assertEquals(30, samples.size());

        // 验证标签分布
        long positiveCount = samples.stream().filter(s -> s.getLabel() == 2).count();
        long negativeCount = samples.stream().filter(s -> s.getLabel() == 0).count();
        long neutralCount = samples.stream().filter(s -> s.getLabel() == 1).count();

        assertTrue(positiveCount > 0);
        assertTrue(negativeCount > 0);
        assertTrue(neutralCount > 0);
    }

    @Test
    void testSplitDataset() {
        List<TrainingSample> samples = preprocessor.generateSyntheticData(100);
        SentimentDataPreprocessor.DatasetSplit split = preprocessor.splitDataset(samples);

        assertNotNull(split);
        assertNotNull(split.getTrainData());
        assertNotNull(split.getValData());

        int trainSize = split.getTrainData().size();
        int valSize = split.getValData().size();

        assertTrue(trainSize >= 70 && trainSize <= 90); // 80% ± tolerance
        assertTrue(valSize >= 10 && valSize <= 30);
        assertEquals(100, trainSize + valSize);
    }
}
