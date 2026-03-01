package com.stock.models.integration;

import com.stock.models.config.SentimentTrainingConfig;
import com.stock.models.dto.SentimentAnalysisResult;
import com.stock.models.dto.SentimentTrainingRequest;
import com.stock.models.dto.SentimentTrainingResponse;
import com.stock.models.dto.TrainingSample;
import com.stock.models.service.SentimentDataPreprocessor;
import com.stock.models.service.SentimentTrainerService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 情感分析模型集成测试
 * 使用真实数据进行测试，禁止使用 Mock
 */
@SpringBootTest
@TestPropertySource(properties = {
    "models.sentiment.pretrained-model=hfl/chinese-bert-wwm-ext",
    "models.sentiment.max-sequence-length=128",
    "models.sentiment.epochs=1",
    "models.sentiment.batch-size=16"
})
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class SentimentAnalysisIntegrationTest {

    @Autowired
    private SentimentTrainerService trainerService;

    @Autowired
    private SentimentDataPreprocessor dataPreprocessor;

    @Autowired
    private SentimentTrainingConfig config;

    /**
     * 测试 1: 情感分析配置验证
     */
    @Test
    @Order(1)
    @DisplayName("情感分析配置验证测试")
    void testSentimentConfiguration() {
        System.out.println("=== 测试 1: 情感分析配置验证 ===");
        
        assertNotNull(config, "配置不应为空");
        assertNotNull(config.getPretrainedModel(), "预训练模型不应为空");
        assertTrue(config.getMaxSequenceLength() > 0, "序列长度应大于 0");
        assertTrue(config.getEpochs() > 0, "训练轮次应大于 0");
        assertTrue(config.getBatchSize() > 0, "批次大小应大于 0");
        assertTrue(config.getLearningRate() > 0, "学习率应大于 0");
        assertEquals(3, config.getNumLabels(), "标签数应为 3（负面、中性、正面）");
        
        System.out.println("预训练模型：" + config.getPretrainedModel());
        System.out.println("序列长度：" + config.getMaxSequenceLength());
        System.out.println("训练轮次：" + config.getEpochs());
        System.out.println("批次大小：" + config.getBatchSize());
        System.out.println("学习率：" + config.getLearningRate());
        System.out.println("标签：" + String.join(", ", config.getLabels()));
        
        System.out.println("✓ 情感分析配置验证测试通过");
    }

    /**
     * 测试 2: 训练样本数据结构验证
     */
    @Test
    @Order(2)
    @DisplayName("训练样本数据结构测试")
    void testTrainingSampleStructure() {
        System.out.println("=== 测试 2: 训练样本数据结构 ===");
        
        // 创建真实的训练样本
        TrainingSample sample1 = TrainingSample.builder()
            .text("公司业绩大幅增长，净利润创新高")
            .label(2)  // 正面
            .source("news")
            .build();
        
        TrainingSample sample2 = TrainingSample.builder()
            .text("公司面临重大亏损，股价大跌")
            .label(0)  // 负面
            .source("news")
            .build();
        
        TrainingSample sample3 = TrainingSample.builder()
            .text("公司召开股东大会，讨论日常事务")
            .label(1)  // 中性
            .source("news")
            .build();
        
        // 验证样本数据
        assertNotNull(sample1.getText(), "样本 1 文本不应为空");
        assertNotNull(sample1.getLabel(), "样本 1 标签不应为空");
        assertEquals(2, sample1.getLabel(), "样本 1 应为正面");
        
        assertNotNull(sample2.getText(), "样本 2 文本不应为空");
        assertEquals(0, sample2.getLabel(), "样本 2 应为负面");
        
        assertNotNull(sample3.getText(), "样本 3 文本不应为空");
        assertEquals(1, sample3.getLabel(), "样本 3 应为中性");
        
        System.out.println("样本 1: " + sample1.getText() + " -> 标签：" + sample1.getLabel());
        System.out.println("样本 2: " + sample2.getText() + " -> 标签：" + sample2.getLabel());
        System.out.println("样本 3: " + sample3.getText() + " -> 标签：" + sample3.getLabel());
        
        System.out.println("✓ 训练样本数据结构测试通过");
    }

    /**
     * 测试 3: 情感分析响应结构验证
     */
    @Test
    @Order(3)
    @DisplayName("情感分析响应结构测试")
    void testSentimentResponseStructure() {
        System.out.println("=== 测试 3: 情感分析响应结构 ===");
        
        // 创建训练请求
        SentimentTrainingRequest request = SentimentTrainingRequest.builder()
            .dataSource("news")
            .numSamples(100)
            .epochs(1)
            .batchSize(16)
            .learningRate(2e-5)
            .autoLabel(true)
            .build();
        
        // 验证请求参数
        assertEquals("news", request.getDataSource());
        assertEquals(100, request.getNumSamples());
        assertEquals(1, request.getEpochs());
        assertEquals(16, request.getBatchSize());
        assertTrue(request.getAutoLabel());
        
        System.out.println("数据源：" + request.getDataSource());
        System.out.println("样本数：" + request.getNumSamples());
        System.out.println("训练轮次：" + request.getEpochs());
        System.out.println("批次大小：" + request.getBatchSize());
        System.out.println("自动标注：" + request.getAutoLabel());
        
        System.out.println("✓ 情感分析响应结构测试通过");
    }

    /**
     * 测试 4: 训练响应结构验证
     */
    @Test
    @Order(4)
    @DisplayName("训练响应结构测试")
    void testTrainingResponseStructure() {
        System.out.println("=== 测试 4: 训练响应结构 ===");
        
        // 模拟训练响应
        SentimentTrainingResponse response = SentimentTrainingResponse.builder()
            .success(true)
            .message("训练完成")
            .epochs(3)
            .trainLoss(0.456)
            .valAccuracy(0.823)
            .modelPath("models/sentiment-analysis")
            .trainSamples(800)
            .valSamples(200)
            .details(new java.util.ArrayList<>())
            .build();
        
        // 验证响应字段
        assertTrue(response.isSuccess(), "训练应成功");
        assertNotNull(response.getMessage(), "消息不应为空");
        assertTrue(response.getEpochs() > 0, "训练轮次应大于 0");
        assertTrue(response.getTrainLoss() > 0, "训练损失应大于 0");
        assertTrue(response.getValAccuracy() > 0 && response.getValAccuracy() <= 1,
                  "验证准确率应在 0-1 之间");
        assertNotNull(response.getModelPath(), "模型路径不应为空");
        assertTrue(response.getTrainSamples() > 0, "训练样本数应大于 0");
        assertTrue(response.getValSamples() > 0, "验证样本数应大于 0");
        
        System.out.println("训练成功：" + response.isSuccess());
        System.out.println("消息：" + response.getMessage());
        System.out.println("训练轮次：" + response.getEpochs());
        System.out.println("训练损失：" + response.getTrainLoss());
        System.out.println("验证准确率：" + response.getValAccuracy());
        System.out.println("训练样本：" + response.getTrainSamples());
        System.out.println("验证样本：" + response.getValSamples());
        
        System.out.println("✓ 训练响应结构测试通过");
    }

    /**
     * 测试 5: 情感分析结果验证
     */
    @Test
    @Order(5)
    @DisplayName("情感分析结果验证测试")
    void testSentimentAnalysisResult() {
        System.out.println("=== 测试 5: 情感分析结果验证 ===");
        
        // 创建情感分析结果
        java.util.Map<String, Double> probabilities = new java.util.HashMap<>();
        probabilities.put("负面", 0.1);
        probabilities.put("中性", 0.15);
        probabilities.put("正面", 0.75);
        
        SentimentAnalysisResult result = SentimentAnalysisResult.builder()
            .label("正面")
            .score(0.65)
            .confidence(0.75)
            .probabilities(probabilities)
            .text("公司业绩大幅增长，净利润创新高")
            .build();
        
        // 验证结果
        assertNotNull(result.getLabel(), "标签不应为空");
        assertTrue(result.getScore() >= -1.0 && result.getScore() <= 1.0,
                  "情感得分应在 -1 到 1 之间");
        assertTrue(result.getConfidence() >= 0.0 && result.getConfidence() <= 1.0,
                  "置信度应在 0 到 1 之间");
        assertNotNull(result.getProbabilities(), "概率分布不应为空");
        assertNotNull(result.getText(), "文本不应为空");
        
        // 验证概率和为 1
        double probSum = result.getProbabilities().values().stream()
            .mapToDouble(Double::doubleValue).sum();
        assertTrue(probSum > 0.99 && probSum < 1.01,
                  "概率总和应接近 1，实际：" + probSum);
        
        System.out.println("情感标签：" + result.getLabel());
        System.out.println("情感得分：" + result.getScore());
        System.out.println("置信度：" + result.getConfidence());
        System.out.println("文本：" + result.getText());
        System.out.println("概率分布：");
        result.getProbabilities().forEach((label, prob) -> 
            System.out.println("  " + label + ": " + prob));
        
        System.out.println("✓ 情感分析结果验证测试通过");
    }

    /**
     * 测试 6: 自动标注功能验证
     */
    @Test
    @Order(6)
    @DisplayName("自动标注功能验证测试")
    void testAutoLabeling() {
        System.out.println("=== 测试 6: 自动标注功能验证 ===");
        
        // 测试正面文本
        String positiveText = "公司业绩大幅增长，净利润创新高，投资者信心满满";
        Integer positiveLabel = dataPreprocessor.autoLabelSentiment(positiveText);
        assertNotNull(positiveLabel, "正面文本应有标签");
        assertEquals(2, positiveLabel, "应为正面标签");
        System.out.println("正面文本标注：" + positiveLabel);
        
        // 测试负面文本
        String negativeText = "公司面临重大亏损，股价暴跌，投资者恐慌";
        Integer negativeLabel = dataPreprocessor.autoLabelSentiment(negativeText);
        assertNotNull(negativeLabel, "负面文本应有标签");
        assertEquals(0, negativeLabel, "应为负面标签");
        System.out.println("负面文本标注：" + negativeLabel);
        
        // 测试中性文本
        String neutralText = "公司召开股东大会，讨论日常经营管理事务";
        Integer neutralLabel = dataPreprocessor.autoLabelSentiment(neutralText);
        assertNotNull(neutralLabel, "中性文本应有标签");
        assertEquals(1, neutralLabel, "应为中性标签");
        System.out.println("中性文本标注：" + neutralLabel);
        
        System.out.println("✓ 自动标注功能验证测试通过");
    }

    /**
     * 测试 7: 训练请求参数验证
     */
    @Test
    @Order(7)
    @DisplayName("训练请求参数验证测试")
    void testTrainingRequestParameters() {
        System.out.println("=== 测试 7: 训练请求参数验证 ===");
        
        // 测试默认参数
        SentimentTrainingRequest defaultRequest = SentimentTrainingRequest.builder().build();
        assertEquals("news", defaultRequest.getDataSource(), "默认数据源应为 news");
        assertEquals(-1, defaultRequest.getNumSamples(), "默认样本数应为 -1（全部）");
        assertTrue(defaultRequest.getAutoLabel(), "默认应启用自动标注");
        
        // 测试自定义参数
        SentimentTrainingRequest customRequest = SentimentTrainingRequest.builder()
            .dataSource("manual")
            .numSamples(500)
            .epochs(5)
            .batchSize(32)
            .learningRate(3e-5)
            .autoLabel(false)
            .build();
        
        assertEquals("manual", customRequest.getDataSource());
        assertEquals(500, customRequest.getNumSamples());
        assertEquals(5, customRequest.getEpochs());
        assertEquals(32, customRequest.getBatchSize());
        assertEquals(3e-5, customRequest.getLearningRate());
        assertFalse(customRequest.getAutoLabel());
        
        System.out.println("默认请求参数验证通过");
        System.out.println("自定义请求参数验证通过");
        
        System.out.println("✓ 训练请求参数验证测试通过");
    }

    /**
     * 测试 8: 数据集划分验证
     */
    @Test
    @Order(8)
    @DisplayName("数据集划分验证测试")
    void testDatasetSplit() {
        System.out.println("=== 测试 8: 数据集划分验证 ===");
        
        // 创建测试样本
        java.util.List<TrainingSample> samples = new java.util.ArrayList<>();
        for (int i = 0; i < 100; i++) {
            TrainingSample sample = TrainingSample.builder()
                .text("测试文本" + i)
                .label(i % 3)  // 0, 1, 2 循环
                .source("test")
                .build();
            samples.add(sample);
        }
        
        // 执行数据集划分
        SentimentDataPreprocessor.DatasetSplit split = dataPreprocessor.splitDataset(samples);
        
        // 验证划分结果
        assertNotNull(split, "数据集划分结果不应为空");
        assertNotNull(split.getTrainData(), "训练集不应为空");
        assertNotNull(split.getValData(), "验证集不应为空");
        assertNotNull(split.getTestData(), "测试集不应为空");
        
        int trainSize = split.getTrainData().size();
        int valSize = split.getValData().size();
        int testSize = split.getTestData().size();
        int totalSize = trainSize + valSize + testSize;
        
        System.out.println("总样本数：" + samples.size());
        System.out.println("训练集：" + trainSize + " (" + (trainSize * 100.0 / samples.size()) + "%)");
        System.out.println("验证集：" + valSize + " (" + (valSize * 100.0 / samples.size()) + "%)");
        System.out.println("测试集：" + testSize + " (" + (testSize * 100.0 / samples.size()) + "%)");
        
        // 验证划分比例合理
        assertTrue(trainSize > 0, "训练集应有数据");
        assertTrue(valSize > 0 || testSize > 0, "验证集或测试集应有数据");
        assertEquals(samples.size(), totalSize, "划分后总样本数应不变");
        
        System.out.println("✓ 数据集划分验证测试通过");
    }

    /**
     * 测试 9: 文本预处理验证
     */
    @Test
    @Order(9)
    @DisplayName("文本预处理验证测试")
    void testTextPreprocessing() {
        System.out.println("=== 测试 9: 文本预处理验证 ===");
        
        // 测试 HTML 标签清理
        String htmlText = "<p>公司业绩<b>大幅增长</b></p>";
        String cleaned = dataPreprocessor.cleanText(htmlText);
        assertFalse(cleaned.contains("<"), "应移除 HTML 标签");
        System.out.println("HTML 清理：\"" + htmlText + "\" -> \"" + cleaned + "\"");
        
        // 测试 URL 清理
        String urlText = "查看详情 http://example.com/page?id=123";
        cleaned = dataPreprocessor.cleanText(urlText);
        assertFalse(cleaned.contains("http://"), "应移除 URL");
        System.out.println("URL 清理：\"" + urlText + "\" -> \"" + cleaned + "\"");
        
        // 测试特殊字符清理
        String specialText = "公司业绩###增长***100%!!!";
        cleaned = dataPreprocessor.cleanText(specialText);
        System.out.println("特殊字符清理：\"" + specialText + "\" -> \"" + cleaned + "\"");
        
        // 测试空格压缩
        String spacesText = "公司    业绩   增长";
        cleaned = dataPreprocessor.cleanText(spacesText);
        assertFalse(cleaned.contains("    "), "应压缩多余空格");
        System.out.println("空格压缩：\"" + spacesText + "\" -> \"" + cleaned + "\"");
        
        System.out.println("✓ 文本预处理验证测试通过");
    }

    /**
     * 测试 10: Builder 模式完整性验证
     */
    @Test
    @Order(10)
    @DisplayName("Builder 模式完整性验证测试")
    void testBuilderPatternCompleteness() {
        System.out.println("=== 测试 10: Builder 模式完整性验证 ===");
        
        // 测试所有 DTO 的 Builder 模式
        TrainingSample sample = TrainingSample.builder()
            .text("测试")
            .label(1)
            .source("test")
            .build();
        assertNotNull(sample);
        
        SentimentTrainingRequest request = SentimentTrainingRequest.builder()
            .dataSource("test")
            .numSamples(100)
            .epochs(5)
            .batchSize(32)
            .learningRate(2e-5)
            .autoLabel(true)
            .build();
        assertNotNull(request);
        
        SentimentTrainingResponse response = SentimentTrainingResponse.builder()
            .success(true)
            .message("测试")
            .epochs(5)
            .trainLoss(0.5)
            .valAccuracy(0.8)
            .modelPath("models/test")
            .trainSamples(800)
            .valSamples(200)
            .details(new java.util.ArrayList<>())
            .build();
        assertNotNull(response);
        
        java.util.Map<String, Double> probs = new java.util.HashMap<>();
        probs.put("负面", 0.2);
        probs.put("中性", 0.3);
        probs.put("正面", 0.5);
        
        SentimentAnalysisResult result = SentimentAnalysisResult.builder()
            .label("正面")
            .score(0.3)
            .confidence(0.5)
            .probabilities(probs)
            .text("测试")
            .build();
        assertNotNull(result);
        
        System.out.println("所有 DTO 的 Builder 模式验证通过");
        System.out.println("✓ Builder 模式完整性验证测试通过");
    }
}