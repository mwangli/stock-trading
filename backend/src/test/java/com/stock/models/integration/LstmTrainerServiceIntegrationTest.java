package com.stock.models.integration;

import com.stock.databus.entity.StockPrice;
import com.stock.databus.repository.PriceRepository;
import com.stock.models.config.LstmTrainingConfig;
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
 * LSTM 模型集成测试
 * 使用真实数据库数据进行测试
 */
@SpringBootTest
@TestPropertySource(properties = {
    "models.lstm.sequence-length=10",
    "models.lstm.epochs=1",
    "models.lstm.batch-size=16",
    "models.lstm.learning-rate=0.001"
})
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class LstmTrainerServiceIntegrationTest {

    @Autowired
    private LstmTrainerService trainerService;

    @Autowired
    private PriceRepository priceRepository;

    @Autowired
    private LstmTrainingConfig config;

    private static final String TEST_STOCK_CODE = "600519";
    private static List<StockPrice> testData = new ArrayList<>();

    /**
     * 测试前准备：插入真实的测试数据
     */
    @BeforeEach
    void setUp() {
        // 清理旧的测试数据
        priceRepository.deleteByCode(TEST_STOCK_CODE);
        
        // 创建 100 天的真实股票价格数据用于测试
        testData = createRealisticStockData(TEST_STOCK_CODE, 100);
        priceRepository.saveAll(testData);
        
        System.out.println("已准备 " + testData.size() + " 条测试数据");
    }

    /**
     * 测试 1: LSTM 训练流程 - 使用真实数据
     */
    @Test
    @Order(1)
    @DisplayName("LSTM 训练流程测试 - 真实数据")
    void testLstmTrainingWithRealData() {
        System.out.println("开始 LSTM 训练测试...");
        
        // 执行训练（简化版本）
        LstmTrainerService.TrainingResult result = trainerService.trainModel(
            TEST_STOCK_CODE,
            100,  // 使用 100 天数据
            1,    // 1 个 epoch 用于快速测试
            16,   // batch size
            0.001 // learning rate
        );
        
        // 验证训练结果
        assertNotNull(result, "训练结果不应为空");
        assertTrue(result.isSuccess(), "训练应该成功");
        assertNotNull(result.getMessage(), "应包含训练消息");
        assertNotNull(result.getModelPath(), "应包含模型路径");
        assertTrue(result.getEpochs() > 0, "训练轮次应大于 0");
        
        System.out.println("训练成功完成：" + result.getMessage());
        System.out.println("模型路径：" + result.getModelPath());
        System.out.println("训练样本数：" + result.getTrainSamples());
        System.out.println("验证样本数：" + result.getValSamples());
    }

    /**
     * 测试 2: 数据预处理验证 - 验证数据被正确加载和预处理
     */
    @Test
    @Order(2)
    @DisplayName("数据预处理验证")
    void testDataPreprocessing() {
        System.out.println("开始数据预处理验证...");
        
        // 从数据库加载数据
        List<StockPrice> prices = priceRepository.findByCodeOrderByDateAsc(TEST_STOCK_CODE);
        
        // 验证数据已正确加载
        assertNotNull(prices, "价格数据不应为空");
        assertFalse(prices.isEmpty(), "价格数据不应为空列表");
        assertTrue(prices.size() >= 60, "应有至少 60 天的数据用于训练");
        
        // 验证数据完整性
        for (StockPrice price : prices) {
            assertNotNull(price.getTodayOpenPrice(), "开盘价不应为空");
            assertNotNull(price.getPrice1(), "最高价不应为空");
            assertNotNull(price.getPrice2(), "最低价不应为空");
            assertNotNull(price.getPrice3(), "收盘价不应为空");
            assertNotNull(price.getTradingVolume(), "成交量不应为空");
            
            // 验证价格为正值
            assertTrue(price.getTodayOpenPrice().compareTo(BigDecimal.ZERO) > 0, "开盘价应为正值");
            assertTrue(price.getPrice3().compareTo(BigDecimal.ZERO) > 0, "收盘价应为正值");
        }
        
        System.out.println("数据验证通过，共 " + prices.size() + " 条记录");
    }

    /**
     * 测试 3: 多股票联合训练
     */
    @Test
    @Order(3)
    @DisplayName("多股票联合训练测试")
    void testMultiStockTraining() {
        System.out.println("开始多股票联合训练测试...");
        
        // 准备第二只股票的数据
        String stockCode2 = "000001";
        List<StockPrice> testData2 = createRealisticStockData(stockCode2, 100);
        priceRepository.saveAll(testData2);
        
        // 执行双股票训练
        String stockCodes = TEST_STOCK_CODE + "," + stockCode2;
        LstmTrainerService.TrainingResult result = trainerService.trainModel(
            stockCodes,
            100,
            1,
            16,
            0.001
        );
        
        // 验证结果
        assertNotNull(result);
        assertTrue(result.isSuccess(), "多股票训练应该成功");
        assertTrue(result.getTrainSamples() > 0, "应有训练样本");
        
        System.out.println("多股票训练完成，训练样本数：" + result.getTrainSamples());
    }

    /**
     * 测试 4: 数据不足场景
     */
    @Test
    @Order(4)
    @DisplayName("数据不足场景测试")
    void testInsufficientData() {
        System.out.println("开始数据不足场景测试...");
        
        // 创建不足 60 天的数据
        String insufficientCode = "999999";
        List<StockPrice> insufficientData = createRealisticStockData(insufficientCode, 30);
        priceRepository.saveAll(insufficientData);
        
        // 尝试训练（应失败）
        LstmTrainerService.TrainingResult result = trainerService.trainModel(
            insufficientCode,
            30,
            1,
            16,
            0.001
        );
        
        // 验证训练失败
        assertNotNull(result);
        assertFalse(result.isSuccess(), "数据不足时训练应失败");
        assertTrue(result.getMessage().contains("没有足够的训练数据"), "应提示数据不足");
        
        System.out.println("数据不足测试通过：" + result.getMessage());
    }

    /**
     * 测试 5: 配置参数验证
     */
    @Test
    @Order(5)
    @DisplayName("配置参数验证")
    void testConfigurationParameters() {
        System.out.println("开始配置参数验证...");
        
        // 验证配置加载正确
        assertNotNull(config, "配置不应为空");
        assertTrue(config.getSequenceLength() > 0, "序列长度应大于 0");
        assertTrue(config.getEpochs() > 0, "训练轮次应大于 0");
        assertTrue(config.getBatchSize() > 0, "批次大小应大于 0");
        assertTrue(config.getLearningRate() > 0, "学习率应大于 0");
        assertTrue(config.getTrainRatio() > 0 && config.getTrainRatio() < 1, "训练比例应在 0-1 之间");
        
        System.out.println("配置参数验证通过：");
        System.out.println("  - 序列长度：" + config.getSequenceLength());
        System.out.println("  - 训练轮次：" + config.getEpochs());
        System.out.println("  - 批次大小：" + config.getBatchSize());
        System.out.println("  - 学习率：" + config.getLearningRate());
    }

    /**
     * 测试 6: TrainingResult Builder 模式
     */
    @Test
    @Order(6)
    @DisplayName("TrainingResult Builder 模式测试")
    void testTrainingResultBuilder() {
        System.out.println("开始 Builder 模式测试...");
        
        LstmTrainerService.TrainingResult result = LstmTrainerService.TrainingResult.builder()
            .success(true)
            .message("测试成功")
            .epochs(10)
            .trainLoss(0.05)
            .valLoss(0.06)
            .modelPath("models/test")
            .trainSamples(100)
            .valSamples(20)
            .details(new ArrayList<>())
            .build();
        
        // 验证所有字段
        assertTrue(result.isSuccess());
        assertEquals("测试成功", result.getMessage());
        assertEquals(10, result.getEpochs());
        assertEquals(0.05, result.getTrainLoss());
        assertEquals(0.06, result.getValLoss());
        assertEquals("models/test", result.getModelPath());
        assertEquals(100, result.getTrainSamples());
        assertEquals(20, result.getValSamples());
        
        System.out.println("Builder 模式测试通过");
    }

    /**
     * 创建真实的股票价格数据
     * 模拟真实股票价格走势（如贵州茅台）
     */
    private List<StockPrice> createRealisticStockData(String stockCode, int days) {
        List<StockPrice> prices = new ArrayList<>();
        
        // 使用类似贵州茅台的真实价格范围
        BigDecimal basePrice = new BigDecimal("1700.00"); // 基础价格
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
            
            // 创建价格对象
            StockPrice price = new StockPrice();
            price.setCode(stockCode);
            price.setDate(date);
            
            // 开盘价
            BigDecimal openPrice = basePrice.multiply(BigDecimal.ONE.add(BigDecimal.valueOf((Math.random() - 0.5) * 0.02)));
            price.setTodayOpenPrice(openPrice.setScale(2, BigDecimal.ROUND_HALF_UP));
            
            // 最高价
            BigDecimal highPrice = basePrice.multiply(BigDecimal.ONE.add(BigDecimal.valueOf(Math.random() * 0.03)));
            price.setPrice1(highPrice.setScale(2, BigDecimal.ROUND_HALF_UP));
            
            // 最低价
            BigDecimal lowPrice = basePrice.multiply(BigDecimal.ONE.subtract(BigDecimal.valueOf(Math.random() * 0.03)));
            price.setPrice2(lowPrice.setScale(2, BigDecimal.ROUND_HALF_UP));
            
            // 收盘价
            BigDecimal closePrice = basePrice.multiply(BigDecimal.ONE.add(BigDecimal.valueOf((Math.random() - 0.5) * 0.02)));
            price.setPrice3(closePrice.setScale(2, BigDecimal.ROUND_HALF_UP));
            
            // 成交量（万手）
            BigDecimal volume = new BigDecimal(String.valueOf(10000 + Math.random() * 50000));
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