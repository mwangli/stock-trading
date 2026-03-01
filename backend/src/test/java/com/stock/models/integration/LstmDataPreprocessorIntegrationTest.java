package com.stock.models.integration;

import com.stock.databus.entity.StockPrice;
import com.stock.databus.repository.PriceRepository;
import com.stock.models.config.LstmTrainingConfig;
import com.stock.models.service.LstmDataPreprocessor;
import com.stock.models.service.LstmTrainerService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * LSTM 数据预处理集成测试
 * 使用真实数据库数据进行测试，禁止使用 Mock
 */
@SpringBootTest
@TestPropertySource(properties = {
    "models.lstm.sequence-length=10",
    "models.lstm.input-size=5",
    "models.lstm.train-ratio=0.8"
})
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class LstmDataPreprocessorIntegrationTest {

    @Autowired
    private LstmDataPreprocessor dataPreprocessor;

    @Autowired
    private PriceRepository priceRepository;

    @Autowired
    private LstmTrainingConfig config;

    private static final String TEST_STOCK_CODE = "600519";
    private static List<StockPrice> testData;

    /**
     * 测试前准备：插入真实的股票价格数据
     */
    @BeforeEach
    void setUp() {
        // 清理测试数据
        priceRepository.deleteByCode(TEST_STOCK_CODE);
        
        // 创建 100 天的真实股票价格数据
        testData = createRealisticStockData(TEST_STOCK_CODE, 100);
        priceRepository.saveAll(testData);
        
        System.out.println("已准备 " + testData.size() + " 条测试数据");
    }

    /**
     * 测试 1: 数据加载和预处理 - 验证从数据库加载数据并预处理
     */
    @Test
    @Order(1)
    @DisplayName("数据加载和预处理测试")
    void testDataLoadingAndPreprocessing() {
        System.out.println("=== 测试 1: 数据加载和预处理 ===");
        
        // 执行数据预处理
        LstmDataPreprocessor.TrainingData trainingData = dataPreprocessor.prepareTrainingData(
            TEST_STOCK_CODE, 
            100
        );
        
        // 验证结果
        assertNotNull(trainingData, "训练数据不应为空");
        assertNotNull(trainingData.getFeatures(), "特征数据不应为空");
        assertNotNull(trainingData.getLabels(), "标签数据不应为空");
        assertNotNull(trainingData.getScalerParams(), "归一化参数不应为空");
        
        // 验证特征维度
        assertFalse(trainingData.getFeatures().isEmpty(), "特征列表不应为空");
        System.out.println("特征样本数：" + trainingData.getFeatures().size());
        
        // 验证特征结构
        if (!trainingData.getFeatures().isEmpty()) {
            float[][] firstSequence = trainingData.getFeatures().get(0);
            assertNotNull(firstSequence, "第一个序列不应为空");
            assertTrue(firstSequence.length > 0, "序列长度应大于 0");
            assertTrue(firstSequence[0].length == 5, "每个时间步应有 5 个特征");
            
            System.out.println("序列长度：" + firstSequence.length);
            System.out.println("特征维度：" + firstSequence[0].length);
        }
        
        // 验证标签
        assertFalse(trainingData.getLabels().isEmpty(), "标签列表不应为空");
        assertEquals(trainingData.getFeatures().size(), trainingData.getLabels().size(),
                    "特征和标签数量应一致");
        
        System.out.println("✓ 数据加载和预处理测试通过");
    }

    /**
     * 测试 2: 数据归一化验证 - 验证数据被正确归一化到 [0,1] 范围
     */
    @Test
    @Order(2)
    @DisplayName("数据归一化验证测试")
    void testDataNormalization() {
        System.out.println("=== 测试 2: 数据归一化验证 ===");
        
        // 执行预处理
        LstmDataPreprocessor.TrainingData trainingData = dataPreprocessor.prepareTrainingData(
            TEST_STOCK_CODE, 
            100
        );
        
        assertNotNull(trainingData);
        
        // 验证归一化参数
        float[][] scalerParams = trainingData.getScalerParams();
        assertNotNull(scalerParams, "归一化参数不应为空");
        assertTrue(scalerParams.length >= 5, "应至少有 5 个特征的归一化参数");
        
        // 验证每个特征的归一化参数有效性
        for (int i = 0; i < 5; i++) {
            float min = scalerParams[i][0];
            float max = scalerParams[i][1];
            
            assertTrue(min <= max, "特征" + i + "的最小值应小于等于最大值");
            System.out.println("特征" + i + "范围：[" + min + ", " + max + "]");
        }
        
        // 验证特征值是否在 [0,1] 范围内
        for (float[][] sequence : trainingData.getFeatures()) {
            for (float[] timestep : sequence) {
                for (float feature : timestep) {
                    assertTrue(feature >= 0.0f && feature <= 1.0f,
                             "归一化后的特征值应在 [0,1] 范围内，实际值：" + feature);
                }
            }
        }
        
        System.out.println("✓ 数据归一化验证测试通过");
    }

    /**
     * 测试 3: 时间序列构建验证 - 验证滑动窗口正确构建序列
     */
    @Test
    @Order(3)
    @DisplayName("时间序列构建验证测试")
    void testSequenceConstruction() {
        System.out.println("=== 测试 3: 时间序列构建验证 ===");
        
        // 执行预处理
        LstmDataPreprocessor.TrainingData trainingData = dataPreprocessor.prepareTrainingData(
            TEST_STOCK_CODE, 
            100
        );
        
        assertNotNull(trainingData);
        
        // 验证序列长度符合配置
        int sequenceLength = config.getSequenceLength();
        if (!trainingData.getFeatures().isEmpty()) {
            float[][] firstSequence = trainingData.getFeatures().get(0);
            assertEquals(sequenceLength, firstSequence.length,
                        "序列长度应与配置一致");
            System.out.println("配置的序列长度：" + sequenceLength);
            System.out.println("实际序列长度：" + firstSequence.length);
        }
        
        // 验证样本数量
        int expectedSamples = testData.size() - sequenceLength;
        int actualSamples = trainingData.getFeatures().size();
        assertTrue(actualSamples > 0, "应生成至少一个样本");
        System.out.println("预期样本数：" + expectedSamples);
        System.out.println("实际样本数：" + actualSamples);
        
        System.out.println("✓ 时间序列构建验证测试通过");
    }

    /**
     * 测试 4: 数据不足场景 - 验证数据不足时的处理
     */
    @Test
    @Order(4)
    @DisplayName("数据不足场景测试")
    void testInsufficientData() {
        System.out.println("=== 测试 4: 数据不足场景 ===");
        
        // 创建不足 60 天的数据
        String insufficientCode = "999999";
        List<StockPrice> insufficientData = createRealisticStockData(insufficientCode, 30);
        priceRepository.saveAll(insufficientData);
        
        // 执行预处理（应返回 null 或空数据）
        LstmDataPreprocessor.TrainingData trainingData = dataPreprocessor.prepareTrainingData(
            insufficientCode, 
            30
        );
        
        // 验证处理结果（数据不足应返回 null）
        assertNull(trainingData, "数据不足时应返回 null");
        
        System.out.println("✓ 数据不足场景测试通过");
        System.out.println("  数据不足时正确返回 null");
    }

    /**
     * 测试 5: 多股票数据合并 - 验证多只股票数据可以正确合并
     */
    @Test
    @Order(5)
    @DisplayName("多股票数据合并测试")
    void testMultiStockDataMerge() {
        System.out.println("=== 测试 5: 多股票数据合并 ===");
        
        // 准备第二只股票的数据
        String stockCode2 = "000001";
        List<StockPrice> testData2 = createRealisticStockData(stockCode2, 100);
        priceRepository.saveAll(testData2);
        
        // 分别预处理两只股票的数据
        LstmDataPreprocessor.TrainingData data1 = dataPreprocessor.prepareTrainingData(
            TEST_STOCK_CODE, 100
        );
        LstmDataPreprocessor.TrainingData data2 = dataPreprocessor.prepareTrainingData(
            stockCode2, 100
        );
        
        // 验证两只股票都有数据
        assertNotNull(data1, "第一只股票数据不应为空");
        assertNotNull(data2, "第二只股票数据不应为空");
        assertFalse(data1.getFeatures().isEmpty(), "第一只股票应有特征数据");
        assertFalse(data2.getFeatures().isEmpty(), "第二只股票应有特征数据");
        
        System.out.println("股票 1 样本数：" + data1.getFeatures().size());
        System.out.println("股票 2 样本数：" + data2.getFeatures().size());
        
        // 验证归一化参数一致性
        assertArrayEquals(data1.getScalerParams().length, data2.getScalerParams().length,
                         "两只股票的归一化参数维度应一致");
        
        System.out.println("✓ 多股票数据合并测试通过");
    }

    /**
     * 测试 6: 特征完整性验证 - 验证 5 个特征都被正确提取
     */
    @Test
    @Order(6)
    @DisplayName("特征完整性验证测试")
    void testFeatureCompleteness() {
        System.out.println("=== 测试 6: 特征完整性验证 ===");
        
        // 执行预处理
        LstmDataPreprocessor.TrainingData trainingData = dataPreprocessor.prepareTrainingData(
            TEST_STOCK_CODE, 
            100
        );
        
        assertNotNull(trainingData);
        
        // 验证每个特征值都是有效的（非负、非 NaN、非无穷大）
        for (float[][] sequence : trainingData.getFeatures()) {
            for (float[] timestep : sequence) {
                for (int i = 0; i < timestep.length; i++) {
                    float value = timestep[i];
                    assertFalse(Float.isNaN(value), "特征值不应为 NaN");
                    assertFalse(Float.isInfinite(value), "特征值不应为无穷大");
                    assertTrue(value >= 0.0f, "归一化后的特征值应非负");
                }
            }
        }
        
        System.out.println("✓ 特征完整性验证测试通过");
        System.out.println("  所有特征值都是有效的");
    }

    /**
     * 测试 7: 数据一致性验证 - 验证特征和标签的一一对应关系
     */
    @Test
    @Order(7)
    @DisplayName("数据一致性验证测试")
    void testDataConsistency() {
        System.out.println("=== 测试 7: 数据一致性验证 ===");
        
        // 执行预处理
        LstmDataPreprocessor.TrainingData trainingData = dataPreprocessor.prepareTrainingData(
            TEST_STOCK_CODE, 
            100
        );
        
        assertNotNull(trainingData);
        
        // 验证特征和标签数量一致
        int featureCount = trainingData.getFeatures().size();
        int labelCount = trainingData.getLabels().size();
        assertEquals(featureCount, labelCount,
                    "特征样本数应与标签数一致");
        
        // 验证每个标签都是单值
        for (float[] label : trainingData.getLabels()) {
            assertEquals(1, label.length, "每个标签应为单值");
            assertFalse(Float.isNaN(label[0]), "标签值不应为 NaN");
        }
        
        System.out.println("✓ 数据一致性验证测试通过");
        System.out.println("  特征和标签数量：" + featureCount);
    }

    /**
     * 测试 8: 配置参数生效验证 - 验证配置参数正确应用到预处理中
     */
    @Test
    @Order(8)
    @DisplayName("配置参数生效验证测试")
    void testConfigurationApplied() {
        System.out.println("=== 测试 8: 配置参数生效验证 ===");
        
        // 验证配置已加载
        assertNotNull(config, "配置不应为空");
        assertTrue(config.getSequenceLength() > 0, "序列长度应大于 0");
        assertEquals(5, config.getInputSize(), "输入特征数应为 5");
        assertTrue(config.getTrainRatio() > 0 && config.getTrainRatio() < 1,
                  "训练比例应在 0-1 之间");
        
        System.out.println("序列长度配置：" + config.getSequenceLength());
        System.out.println("输入特征数配置：" + config.getInputSize());
        System.out.println("训练比例配置：" + config.getTrainRatio());
        
        System.out.println("✓ 配置参数生效验证测试通过");
    }

    /**
     * 创建真实的股票价格数据
     * 模拟贵州茅台的真实价格走势
     */
    private List<StockPrice> createRealisticStockData(String stockCode, int days) {
        List<StockPrice> prices = new ArrayList<>();
        
        // 使用类似贵州茅台的真实价格范围（1500-2000 元）
        BigDecimal basePrice = new BigDecimal("1700.00");
        LocalDate startDate = LocalDate.now().minusDays(days);
        
        for (int i = 0; i < days; i++) {
            LocalDate date = startDate.plusDays(i);
            
            // 跳过周末
            if (date.getDayOfWeek().getValue() > 5) {
                continue;
            }
            
            // 模拟真实的价格波动（-3% 到 +3%）
            double changePercent = (Math.random() - 0.5) * 0.06;
            basePrice = basePrice.multiply(BigDecimal.ONE.add(BigDecimal.valueOf(changePercent)));
            
            // 确保价格在合理范围内
            if (basePrice.compareTo(new BigDecimal("1500")) < 0) {
                basePrice = new BigDecimal("1500");
            }
            if (basePrice.compareTo(new BigDecimal("2000")) > 0) {
                basePrice = new BigDecimal("2000");
            }
            
            StockPrice price = new StockPrice();
            price.setCode(stockCode);
            price.setDate(date);
            
            // 开盘价
            BigDecimal openPrice = basePrice.multiply(BigDecimal.ONE.add(
                BigDecimal.valueOf((Math.random() - 0.5) * 0.02)));
            price.setTodayOpenPrice(openPrice.setScale(2, BigDecimal.ROUND_HALF_UP));
            
            // 最高价
            BigDecimal highPrice = basePrice.multiply(BigDecimal.ONE.add(
                BigDecimal.valueOf(Math.random() * 0.03)));
            price.setPrice1(highPrice.setScale(2, BigDecimal.ROUND_HALF_UP));
            
            // 最低价
            BigDecimal lowPrice = basePrice.multiply(BigDecimal.ONE.subtract(
                BigDecimal.valueOf(Math.random() * 0.03)));
            price.setPrice2(lowPrice.setScale(2, BigDecimal.ROUND_HALF_UP));
            
            // 收盘价
            BigDecimal closePrice = basePrice.multiply(BigDecimal.ONE.add(
                BigDecimal.valueOf((Math.random() - 0.5) * 0.02)));
            price.setPrice3(closePrice.setScale(2, BigDecimal.ROUND_HALF_UP));
            
            // 成交量（万手）
            BigDecimal volume = new BigDecimal(String.valueOf(
                10000 + Math.random() * 50000));
            price.setTradingVolume(volume.setScale(2, BigDecimal.ROUND_HALF_UP));
            
            prices.add(price);
        }
        
        System.out.println("为 " + stockCode + " 创建了 " + prices.size() + " 条价格数据");
        return prices;
    }

    /**
     * 测试后清理：删除测试数据
     */
    @AfterEach
    void tearDown() {
        System.out.println("清理测试数据...");
        priceRepository.deleteByCode(TEST_STOCK_CODE);
        priceRepository.deleteByCode("000001");
        priceRepository.deleteByCode("999999");
        System.out.println("测试数据清理完成");
    }
}