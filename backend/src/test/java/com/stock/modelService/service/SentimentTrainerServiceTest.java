package com.stock.modelService.service;

import com.stock.modelService.config.SentimentTrainingConfig;
import com.stock.modelService.dto.SentimentAnalysisResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("情感分析服务单元测试")
class SentimentTrainerServiceTest {

    @Mock
    private SentimentTrainingConfig config;

    @Mock
    private SentimentDataPreprocessor dataPreprocessor;

    @InjectMocks
    private SentimentTrainerService sentimentTrainerService;

    @BeforeEach
    void setUp() {
        // 模拟配置
        lenient().when(config.getLabels()).thenReturn(new String[]{"neutral", "positive", "negative"});
    }

    @Test
    @DisplayName("测试规则分析模式 - 正面情感归一化得分")
    void testAnalyzeWithRules_Positive() {
        // Arrange
        // 1=Positive
        when(dataPreprocessor.autoLabelSentiment(anyString())).thenReturn(1);
        String text = "公司业绩大涨，非常好";

        // Act
        // 模型未加载时会自动调用 analyzeWithRules
        SentimentAnalysisResult result = sentimentTrainerService.analyzeSentiment(text);

        // Assert
        assertNotNull(result);
        assertEquals("positive", result.getLabel());
        assertEquals(0.7, result.getConfidence());
        
        // 正面情感 (Positive) 范围: 60-100
        // 公式: 60 + (confidence * 40) = 60 + (0.7 * 40) = 60 + 28 = 88
        assertEquals(88.0, result.getNormalizedScore(), 0.01);
    }

    @Test
    @DisplayName("测试规则分析模式 - 负面情感归一化得分")
    void testAnalyzeWithRules_Negative() {
        // Arrange
        // 2=Negative
        when(dataPreprocessor.autoLabelSentiment(anyString())).thenReturn(2);
        String text = "公司亏损严重，甚至可能破产";

        // Act
        SentimentAnalysisResult result = sentimentTrainerService.analyzeSentiment(text);

        // Assert
        assertNotNull(result);
        assertEquals("negative", result.getLabel());
        
        // 负面情感 (Negative) 范围: 0-40
        // 公式: 40 - (confidence * 40) = 40 - (0.7 * 40) = 12
        assertEquals(12.0, result.getNormalizedScore(), 0.01);
    }

    @Test
    @DisplayName("测试规则分析模式 - 中性情感归一化得分")
    void testAnalyzeWithRules_Neutral() {
        // Arrange
        // 0=Neutral
        when(dataPreprocessor.autoLabelSentiment(anyString())).thenReturn(0);
        String text = "公司发布了年度报告";

        // Act
        SentimentAnalysisResult result = sentimentTrainerService.analyzeSentiment(text);

        // Assert
        assertNotNull(result);
        assertEquals("neutral", result.getLabel());
        
        // 中性情感 (Neutral) 范围: 40-60
        // 公式: 40 + (confidence * 20) = 40 + (0.7 * 20) = 40 + 14 = 54
        assertEquals(54.0, result.getNormalizedScore(), 0.01);
    }

    @Test
    @DisplayName("测试归一化分数计算逻辑 (私有方法)")
    void testCalculateNormalizedScore() {
        // 使用反射调用私有方法 calculateNormalizedScore
        
        // 1. 测试 Negative (0-40)
        // 概率 0.0 -> 40 (Weak Negative)
        assertEquals(40.0, invokeCalculateNormalizedScore("negative", 0.0), 0.01);
        // 概率 0.5 -> 20
        assertEquals(20.0, invokeCalculateNormalizedScore("negative", 0.5), 0.01);
        // 概率 1.0 -> 0 (Strong Negative)
        assertEquals(0.0, invokeCalculateNormalizedScore("negative", 1.0), 0.01);
        // removed duplicates
        // removed wrong assertion

        // 2. 测试 Neutral (40-60)
        // 概率 0.0 -> 40
        assertEquals(40.0, invokeCalculateNormalizedScore("neutral", 0.0), 0.01);
        // 概率 0.5 -> 50
        assertEquals(50.0, invokeCalculateNormalizedScore("neutral", 0.5), 0.01);
        // 概率 1.0 -> 60
        assertEquals(60.0, invokeCalculateNormalizedScore("neutral", 1.0), 0.01);

        // 3. 测试 Positive (60-100)
        // 概率 0.0 -> 60
        assertEquals(60.0, invokeCalculateNormalizedScore("positive", 0.0), 0.01);
        // 概率 0.5 -> 80
        assertEquals(80.0, invokeCalculateNormalizedScore("positive", 0.5), 0.01);
        // 概率 1.0 -> 100
        assertEquals(100.0, invokeCalculateNormalizedScore("positive", 1.0), 0.01);
    }

    private double invokeCalculateNormalizedScore(String label, double probability) {
        return ReflectionTestUtils.invokeMethod(
                sentimentTrainerService,
                "calculateNormalizedScore",
                label,
                probability
        );
    }
}
