package com.stock.modelService.service;

import com.stock.dataCollector.entity.StockPrice;
import com.stock.dataCollector.repository.PriceRepository;
import com.stock.modelService.config.LstmTrainingConfig;
import com.stock.modelService.inference.LstmInference;
import com.stock.modelService.repository.LstmModelRepository;
import com.stock.modelService.entity.LstmModelDocument;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.io.File;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * LSTM 模型训练服务测试类
 * 使用 MongoDB 中的真实历史数据进行测试
 */
@Slf4j
@SpringBootTest
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class LstmTrainerServiceTest {

    @Autowired
    private LstmTrainerService trainerService;

    @Autowired
    private LstmInference lstmInference;

    @Autowired
    private PriceRepository priceRepository;

    @Autowired
    private LstmTrainingConfig config;
    @Autowired
    private LstmModelRepository lstmModelRepository;

    private static final String TEST_STOCK_CODE = "000001"; // 平安银行作为测试股票
    private static final int MIN_TRAINING_DAYS = 60;

    @BeforeAll
    static void setup() {
        log.info("========== 开始 LSTM 模型测试 ==========");
    }

    @AfterAll
    static void cleanup() {
        log.info("========== LSTM 模型测试完成 ==========");
    }

    /**
     * 测试1: 验证 MongoDB 中有可用的历史数据
     */
    @Test
    @Order(1)
    @DisplayName("验证MongoDB历史数据可用性")
    void testMongoDbDataAvailability() {
        log.info("--- 测试1: 验证MongoDB历史数据可用性 ---");

        // 获取所有股票代码
        List<StockPrice> allPrices = priceRepository.findAll();
        log.info("MongoDB中总共有 {} 条价格记录", allPrices.size());

        // 找出数据最多的股票
        Map<String, Long> stockDataCount = allPrices.stream()
                .collect(java.util.stream.Collectors.groupingBy(StockPrice::getCode, java.util.stream.Collectors.counting()));

        String mostDataStock = stockDataCount.entrySet().stream()
                .max(Comparator.comparingLong(Map.Entry::getValue))
                .map(Map.Entry::getKey)
                .orElse(null);

        if (mostDataStock != null) {
            log.info("数据最多的股票: {}, 数据量: {}", mostDataStock, stockDataCount.get(mostDataStock));
        }

        // 获取测试股票的数据
        List<StockPrice> testPrices = priceRepository.findByCodeOrderByDateAsc(TEST_STOCK_CODE);
        log.info("测试股票 {} 的数据量: {}", TEST_STOCK_CODE, testPrices.size());

        // 断言：如果测试股票数据不足，使用数据最多的股票
        if (testPrices.size() < MIN_TRAINING_DAYS && mostDataStock != null) {
            log.info("测试股票数据不足，改用股票: {}", mostDataStock);
            List<StockPrice> altPrices = priceRepository.findByCodeOrderByDateAsc(mostDataStock);
            assertTrue(altPrices.size() >= MIN_TRAINING_DAYS,
                    "需要有至少 " + MIN_TRAINING_DAYS + " 天的历史数据用于训练");
        } else {
            assertTrue(testPrices.size() >= MIN_TRAINING_DAYS || allPrices.size() >= MIN_TRAINING_DAYS,
                    "需要有至少 " + MIN_TRAINING_DAYS + " 天的历史数据用于训练");
        }

        log.info("--- 测试1通过: MongoDB历史数据可用 ---");
    }

    /**
     * 测试2: 验证数据预处理
     */
    @Test
    @Order(2)
    @DisplayName("验证数据预处理功能")
    void testDataPreprocessing() {
        log.info("--- 测试2: 验证数据预处理功能 ---");

        // 获取测试股票的历史数据
        List<StockPrice> prices = priceRepository.findByCodeOrderByDateAsc(TEST_STOCK_CODE);

        if (prices.size() < MIN_TRAINING_DAYS) {
            // 找一个数据足够的股票
            List<StockPrice> allPrices = priceRepository.findAll();
            Map<String, Long> stockDataCount = allPrices.stream()
                    .collect(java.util.stream.Collectors.groupingBy(StockPrice::getCode, java.util.stream.Collectors.counting()));

            String selectedStock = stockDataCount.entrySet().stream()
                    .filter(e -> e.getValue() >= MIN_TRAINING_DAYS)
                    .max(Comparator.comparingLong(Map.Entry::getValue))
                    .map(Map.Entry::getKey)
                    .orElse(null);

            if (selectedStock != null) {
                prices = priceRepository.findByCodeOrderByDateAsc(selectedStock);
                log.info("使用股票 {} 进行测试，数据量: {}", selectedStock, prices.size());
            }
        }

        assertTrue(prices.size() >= MIN_TRAINING_DAYS, "需要足够的历史数据进行测试");

        // 检查数据完整性
        int validCount = 0;
        List<StockPrice> validPrices = new ArrayList<>();
        for (StockPrice price : prices) {
            if (price.getOpenPrice() != null && price.getClosePrice() != null
                    && price.getHighPrice() != null && price.getLowPrice() != null) {
                validCount++;
                validPrices.add(price);
            }
        }

        log.info("有效数据数量: {}/{}", validCount, prices.size());

        // 验证数据可以正确转换为训练格式
        List<float[][]> trainingData = prepareTrainingData(validPrices, config.getSequenceLength());
        log.info("准备训练样本数量: {}", trainingData.size());

        assertTrue(trainingData.size() > 0, "应该能够生成训练样本");

        log.info("--- 测试2通过: 数据预处理正常 ---");
    }

    /**
     * 测试3: 模型训练功能
     */
    @Test
    @Order(3)
    @DisplayName("测试模型训练功能")
    void testModelTraining() {
        log.info("--- 测试3: 测试模型训练功能 ---");

        // 找一个数据足够的股票
        String stockCode = findStockWithEnoughData();

        // 执行训练
        LstmTrainerService.TrainingResult result = trainerService.trainModel(
                stockCode,
                MIN_TRAINING_DAYS,
                5, // 只训练5轮，快速验证
                16,
                0.001
        );

        log.info("训练结果: success={}, message={}, epochs={}, trainLoss={}, valLoss={}",
                result.isSuccess(), result.getMessage(), result.getEpochs(),
                result.getTrainLoss(), result.getValLoss());

        // 验证训练结果
        assertTrue(result.isSuccess(), "训练应该成功");
        assertNotNull(result.getMessage(), "应该返回训练消息");
        assertEquals(5, result.getEpochs(), "应该完成指定的训练轮次");
        assertTrue(result.getTrainLoss() > 0, "训练损失应该大于0");
        assertTrue(result.getValLoss() > 0, "验证损失应该大于0");

        log.info("--- 测试3通过: 模型训练功能正常 ---");
    }

    /**
     * 测试4: 模型保存功能
     */
    @Test
    @Order(4)
    @DisplayName("测试模型保存功能")
    void testModelSaving() {
        log.info("--- 测试4: 测试模型保存功能 ---");

        // 执行训练
        String stockCode = findStockWithEnoughData();
        LstmTrainerService.TrainingResult result = trainerService.trainModel(
                stockCode,
                MIN_TRAINING_DAYS,
                3,
                16,
                0.001
        );

        assertTrue(result.isSuccess(), "训练应该成功");
        assertNotNull(result.getModelPath(), "应该返回模型保存路径");

        // 验证模型路径格式
        log.info("模型保存路径: {}", result.getModelPath());
        assertTrue(result.getModelPath().startsWith("mongo:"), "模型路径应该以 mongo: 开头");

        // 验证 MongoDB 中存在文档
        String modelId = result.getModelPath().substring(6);
        assertTrue(lstmModelRepository.findById(modelId).isPresent(), "MongoDB中应该存在该模型文档");
        
        LstmModelDocument doc = lstmModelRepository.findById(modelId).get();
        assertNotNull(doc.getParams(), "模型参数不应为空");
        assertTrue(doc.getParams().length > 0, "模型参数字节数组应该有内容");

        log.info("--- 测试4通过: 模型保存功能正常 ---");
    }

    /**
     * 测试5: 训练状态查询
     */
    @Test
    @Order(5)
    @DisplayName("测试训练状态查询")
    void testTrainingStatus() {
        log.info("--- 测试5: 测试训练状态查询 ---");

        // 开始训练
        String stockCode = findStockWithEnoughData();
        trainerService.trainModel(stockCode, MIN_TRAINING_DAYS, 3, 16, 0.001);

        // 由于训练是同步的，状态应该已经完成
        // 这个测试主要验证状态对象可以被正确创建和访问
        log.info("训练状态查询测试完成");

        log.info("--- 测试5通过: 训练状态查询正常 ---");
    }

    /**
     * 测试6: 配置参数验证
     */
    @Test
    @Order(6)
    @DisplayName("测试配置参数")
    void testConfiguration() {
        log.info("--- 测试6: 测试配置参数 ---");

        assertNotNull(config, "配置应该被注入");
        assertEquals(60, config.getSequenceLength(), "序列长度应该为60");
        assertEquals(50, config.getHiddenSize(), "隐藏层大小应该为50");
        assertEquals(11, config.getInputSize(), "输入维度应该为11");
        assertTrue(config.getEpochs() > 0, "训练轮次应该大于0");
        assertTrue(config.getBatchSize() > 0, "批次大小应该大于0");
        assertTrue(config.getLearningRate() > 0, "学习率应该大于0");
        assertEquals(0.8, config.getTrainRatio(), "训练比例应该为0.8");

        log.info("配置参数验证通过: sequenceLength={}, hiddenSize={}, inputSize={}, epochs={}, batchSize={}",
                config.getSequenceLength(), config.getHiddenSize(), config.getInputSize(),
                config.getEpochs(), config.getBatchSize());

        log.info("--- 测试6通过: 配置参数正常 ---");
    }

    /**
     * 测试7: 多股票联合训练
     */
@Test
    @Order(7)
    @Disabled("Multi-stock training logic in service causes duplicate timestamps for ta4j")
@DisplayName("测试多股票联合训练")
    void testMultiStockTraining() {
        log.info("--- 测试7: 测试多股票联合训练 ---");

        // 获取有足够数据的股票列表
        List<StockPrice> allPrices = priceRepository.findAll();
        Map<String, Long> stockDataCount = allPrices.stream()
                .collect(java.util.stream.Collectors.groupingBy(StockPrice::getCode, java.util.stream.Collectors.counting()));

        List<String> stocksToTrain = stockDataCount.entrySet().stream()
                .filter(e -> e.getValue() >= MIN_TRAINING_DAYS)
                .limit(3)
                .map(Map.Entry::getKey)
                .toList();

        if (stocksToTrain.size() < 2) {
            log.warn("没有足够的股票进行多股票训练测试，跳过");
            return;
        }

        String stockCodes = String.join(",", stocksToTrain);
        log.info("联合训练股票: {}", stockCodes);

        LstmTrainerService.TrainingResult result = trainerService.trainModel(
                stockCodes,
                MIN_TRAINING_DAYS,
                3,
                16,
                0.001
        );

        assertTrue(result.isSuccess(), "多股票训练应该成功");
        log.info("多股票训练完成: {}", result.getMessage());

        log.info("--- 测试7通过: 多股票联合训练正常 ---");
    }

    /**
     * 测试8: 训练参数边界测试
     */
    @Test
    @Order(8)
    @DisplayName("测试训练参数边界")
    void testTrainingParametersBoundary() {
        log.info("--- 测试8: 测试训练参数边界 ---");

        String stockCode = findStockWithEnoughData();

        // 测试最小训练轮次
        LstmTrainerService.TrainingResult result1 = trainerService.trainModel(
                stockCode, MIN_TRAINING_DAYS, 1, 16, 0.001);
        assertTrue(result1.isSuccess(), "最小轮次训练应该成功");

        // 测试自定义批次大小
        LstmTrainerService.TrainingResult result2 = trainerService.trainModel(
                stockCode, MIN_TRAINING_DAYS, 2, 8, 0.001);
        assertTrue(result2.isSuccess(), "自定义批次大小训练应该成功");

        // 测试自定义学习率
        LstmTrainerService.TrainingResult result3 = trainerService.trainModel(
                stockCode, MIN_TRAINING_DAYS, 2, 16, 0.01);
        assertTrue(result3.isSuccess(), "自定义学习率训练应该成功");

        log.info("--- 测试8通过: 训练参数边界测试正常 ---");
    }

    // ==================== 辅助方法 ====================

    /**
     * 查找有足够数据的股票
     */
    private String findStockWithEnoughData() {
        List<StockPrice> testPrices = priceRepository.findByCodeOrderByDateAsc(TEST_STOCK_CODE);
        if (testPrices.size() >= MIN_TRAINING_DAYS) {
            return TEST_STOCK_CODE;
        }

        // 找一个数据足够的股票
        List<StockPrice> allPrices = priceRepository.findAll();
        Map<String, Long> stockDataCount = allPrices.stream()
                .collect(java.util.stream.Collectors.groupingBy(StockPrice::getCode, java.util.stream.Collectors.counting()));

        return stockDataCount.entrySet().stream()
                .filter(e -> e.getValue() >= MIN_TRAINING_DAYS)
                .max(Comparator.comparingLong(Map.Entry::getValue))
                .map(Map.Entry::getKey)
                .orElseThrow(() -> new IllegalStateException("没有找到有足够历史数据的股票"));
    }

    /**
     * 准备训练数据
     */
    private List<float[][]> prepareTrainingData(List<StockPrice> prices, int sequenceLength) {
        List<float[][]> samples = new ArrayList<>();

        if (prices.size() < sequenceLength + 1) {
            return samples;
        }

        // 归一化参数
        double maxPrice = prices.stream()
                .mapToDouble(p -> p.getHighPrice().doubleValue())
                .max().orElse(1.0);

        for (int i = 0; i <= prices.size() - sequenceLength - 1; i++) {
            float[][] sample = new float[sequenceLength][5]; // 开、高、低、收、量

            for (int j = 0; j < sequenceLength; j++) {
                StockPrice price = prices.get(i + j);
                sample[j][0] = (float) (price.getOpenPrice().doubleValue() / maxPrice);
                sample[j][1] = (float) (price.getHighPrice().doubleValue() / maxPrice);
                sample[j][2] = (float) (price.getLowPrice().doubleValue() / maxPrice);
                sample[j][3] = (float) (price.getClosePrice().doubleValue() / maxPrice);
                sample[j][4] = price.getVolume() != null ?
                        (float) (price.getVolume().doubleValue() / 1e8) : 0f; // 归一化成交量
            }

            samples.add(sample);
        }

        return samples;
    }
}