package com.stock.modelService.integration;

import com.stock.Application;
import com.stock.modelService.service.SentimentTrainerService;
import com.stock.modelService.dto.SentimentAnalysisResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 情感分析服务集成测试，验证模型加载和分析功能的端到端流程。
 * 该测试会从Hugging Face下载真实模型，因此执行时间可能较长。
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("情感分析服务集成测试")
public class SentimentAnalysisIntegrationTest {

    @Autowired
    private SentimentTrainerService sentimentTrainerService;

    @Test
    @DisplayName("测试正面情绪文本分析")
    void testAnalyzePositiveSentiment() {
        // 准备
        String positiveText = "这家公司的前景非常光明，我非常看好它的发展。";

        // 执行
        SentimentAnalysisResult result = sentimentTrainerService.analyzeSentiment(positiveText);

        // 验证
        assertNotNull(result, "情感分析结果不应为 null");
        assertNotNull(result.getLabel(), "结果标签不应为 null");
        assertTrue(result.getConfidence() > 0, "置信度应大于 0");
        
        // 根据模型的普遍表现，我们预期这是一个正面或中性的评价
        assertTrue(
            "positive".equalsIgnoreCase(result.getLabel()) || "neutral".equalsIgnoreCase(result.getLabel()),
            "对于正面文本，预期结果标签是 'positive' 或 'neutral'，但实际是：" + result.getLabel()
        );

        System.out.println("正面文本分析结果: " + result);
    }

    @Test
    @DisplayName("测试负面情绪文本分析")
    void testAnalyzeNegativeSentiment() {
        // 准备
        String negativeText = "市场崩盘了，我的股票跌得一塌糊涂，真是糟糕的一天。";

        // 执行
        SentimentAnalysisResult result = sentimentTrainerService.analyzeSentiment(negativeText);

        // 验证
        assertNotNull(result, "情感分析结果不应为 null");
        assertNotNull(result.getLabel(), "结果标签不应为 null");
        assertTrue(result.getConfidence() > 0, "置信度应大于 0");

        // 根据模型的普遍表现，我们预期这是一个负面或中性的评价
         assertTrue(
            "negative".equalsIgnoreCase(result.getLabel()) || "neutral".equalsIgnoreCase(result.getLabel()),
            "对于负面文本，预期结果标签是 'negative' 或 'neutral'，但实际是：" + result.getLabel()
        );

        System.out.println("负面文本分析结果: " + result);
    }

    @Test
    @DisplayName("测试模型加载状态")
    void testModelIsLoaded() {
        // 执行 & 验证
        assertTrue(sentimentTrainerService.isModelLoaded(), "模型应该在服务启动后成功加载");
    }
}
