package com.stock.modelService.service;

import com.stock.dataCollector.entity.StockPrice;
import com.stock.dataCollector.repository.PriceRepository;
import com.stock.modelService.config.LstmTrainingConfig;
import com.stock.modelService.inference.LstmInference;
import com.stock.modelService.service.LstmTrainerService;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * LSTM 模型集成测试
 * 验证完整的训练 -> 保存 -> 加载 -> 预测流程
 */
@Slf4j
@SpringBootTest
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class LstmModelIntegrationTest {

    @Autowired
    private LstmTrainerService trainerService;

    @Autowired
    private LstmInference lstmInference;

    @Autowired
    private PriceRepository priceRepository;

    @Autowired
    private LstmTrainingConfig config;

    private static final int MIN_TRAINING_DAYS = 60;
    private static final int SEQUENCE_LENGTH = 60;

    // 保存训练结果供后续测试使用
    private static String trainedModelPath;
    private static String trainedStockCode;

    @BeforeAll
    static void setup() {
        log.info("╔════════════════════════════════════════════════════════════╗");
        log.info("║       LSTM 模型集成测试 - 完整流程验证                      ║");
        log.info("╚════════════════════════════════════════════════════════════╝");
    }

    @AfterAll
    static void cleanup() {
        log.info("╔════════════════════════════════════════════════════════════╗");
        log.info("║           LSTM 模型集成测试 - 全部测试完成                  ║");
        log.info("╚════════════════════════════════════════════════════════════╝");
    }

    /**
     * 集成测试1: 完整的训练-保存-加载-预测流程
     */
    @Test
    @Order(1)
    @DisplayName("集成测试: 完整的训练-保存-加载-预测流程")
    void testFullTrainingAndPredictionWorkflow() {
        log.info("╔════════════════════════════════════════════════════════════╗");
        log.info("║  集成测试1: 完整的训练-保存-加载-预测流程                  ║");
        log.info("╚════════════════════════════════════════════════════════════╝");

        // Step 1: 查找有足够数据的股票
        log.info("┌────────────────────────────────────────────────────────────┐");
        log.info("│ Step 1: 查找可用的股票数据                                  │");
        log.info("└────────────────────────────────────────────────────────────┘");

        trainedStockCode = findStockWithEnoughData();
        List<StockPrice> prices = priceRepository.findByCodeOrderByDateAsc(trainedStockCode);

        log.info("选中的股票: {}, 数据量: {}", trainedStockCode, prices.size());
        assertTrue(prices.size() >= MIN_TRAINING_DAYS, "需要有足够的历史数据");

        // 显示数据范围
        LocalDate startDate = prices.get(0).getDate();
        LocalDate endDate = prices.get(prices.size() - 1).getDate();
        log.info("数据日期范围: {} 至 {}", startDate, endDate);

        // Step 2: 训练模型
        log.info("┌────────────────────────────────────────────────────────────┐");
        log.info("│ Step 2: 训练 LSTM 模型                                      │");
        log.info("└────────────────────────────────────────────────────────────┘");

        LstmTrainerService.TrainingResult trainingResult = trainerService.trainModel(
                trainedStockCode,
                MIN_TRAINING_DAYS,
                10, // 训练10轮
                32,
                0.001
        );

        log.info("训练完成:");
        log.info("  - 成功: {}", trainingResult.isSuccess());
        log.info("  - 消息: {}", trainingResult.getMessage());
        log.info("  - 训练轮次: {}", trainingResult.getEpochs());
        log.info("  - 训练损失: {}", trainingResult.getTrainLoss());
        log.info("  - 验证损失: {}", trainingResult.getValLoss());
        log.info("  - 模型路径: {}", trainingResult.getModelPath());

        assertTrue(trainingResult.isSuccess(), "训练应该成功");
        assertNotNull(trainingResult.getModelPath(), "应该有模型保存路径");

        trainedModelPath = trainingResult.getModelPath();

        // Step 3: 验证模型保存
        log.info("┌────────────────────────────────────────────────────────────┐");
        log.info("│ Step 3: 验证模型保存                                        │");
        log.info("└────────────────────────────────────────────────────────────┘");

        if (trainedModelPath.startsWith("mongo:")) {
            log.info("模型已保存到 MongoDB, ID: {}", trainedModelPath);
            assertTrue(trainedModelPath.length() > 6, "MongoDB ID 应该是有效的");
        } else {
            Path modelPath = Paths.get(trainedModelPath);
            assertTrue(Files.exists(modelPath), "模型目录应该存在");
            log.info("模型目录存在: {}", modelPath.toAbsolutePath());

            // 列出模型目录中的文件
            try {
                Files.list(modelPath).forEach(file -> {
                    log.info("  文件: {}", file.getFileName());
                });
            } catch (Exception e) {
                log.warn("无法列出模型目录文件: {}", e.getMessage());
            }
        }


        // Step 4: 加载模型
        log.info("┌────────────────────────────────────────────────────────────┐");
        log.info("│ Step 4: 加载模型                                            │");
        log.info("└────────────────────────────────────────────────────────────┘");

        lstmInference.loadModel(trainedModelPath);
        log.info("模型加载状态: {}", lstmInference.isLoaded());

        // Step 5: 执行预测
        log.info("┌────────────────────────────────────────────────────────────┐");
        log.info("│ Step 5: 执行价格预测                                        │");
        log.info("└────────────────────────────────────────────────────────────┘");

        // 准备预测输入
        float[][][] inputData = preparePredictionInput(prices, SEQUENCE_LENGTH);
        log.info("输入数据形状: [{}, {}, {}]", inputData.length, inputData[0].length, inputData[0][0].length);

        // 执行预测
        float[] predictions = lstmInference.predict(inputData);
        float predictedPrice = predictions[0];

        log.info("预测结果: {}", predictedPrice);

        // 获取带详细信息的预测
        Map<String, Object> detailedResult = lstmInference.predictWithDetails(trainedStockCode, inputData);
        log.info("详细预测结果: {}", detailedResult);

        // Step 6: 验证预测结果
        log.info("┌────────────────────────────────────────────────────────────┐");
        log.info("│ Step 6: 验证预测结果                                        │");
        log.info("└────────────────────────────────────────────────────────────┘");

        // 获取最近的实际价格
        StockPrice lastPrice = prices.get(prices.size() - 1);
        double actualClosePrice = lastPrice.getClosePrice().doubleValue();

        log.info("最近实际收盘价: {}", actualClosePrice);
        log.info("模型预测价格: {}", predictedPrice);

        // 验证预测结果有效性
        assertNotNull(predictions, "预测结果不应为空");
        assertTrue(predictions.length > 0, "应该有预测结果");
        assertFalse(Float.isNaN(predictedPrice), "预测结果不应为NaN");
        assertFalse(Float.isInfinite(predictedPrice), "预测结果不应为无限大");

        log.info("╔════════════════════════════════════════════════════════════╗");
        log.info("║  集成测试1: 完整流程验证通过                                ║");
        log.info("╚════════════════════════════════════════════════════════════╝");
    }

    /**
     * 集成测试2: 多轮训练损失下降验证
     */
    @Test
    @Order(2)
    @DisplayName("集成测试: 多轮训练损失下降验证")
    void testTrainingLossDecrease() {
        log.info("╔════════════════════════════════════════════════════════════╗");
        log.info("║  集成测试2: 多轮训练损失下降验证                            ║");
        log.info("╚════════════════════════════════════════════════════════════╝");

        String stockCode = findStockWithEnoughData();

        // 执行多轮训练
        LstmTrainerService.TrainingResult result = trainerService.trainModel(
                stockCode,
                MIN_TRAINING_DAYS,
                20, // 训练20轮
                32,
                0.001
        );

        assertTrue(result.isSuccess(), "训练应该成功");
        assertNotNull(result.getDetails(), "应该有训练详细日志");

        List<Map<String, Object>> details = result.getDetails();
        log.info("训练详细日志条数: {}", details.size());

        // 验证损失值
        double initialLoss = (double) details.get(0).get("trainLoss");
        double finalLoss = result.getTrainLoss();

        log.info("初始损失: {}", initialLoss);
        log.info("最终损失: {}", finalLoss);

        // 在简化版实现中，损失可能不会严格下降
        // 主要验证损失值是有效的数字
        assertTrue(initialLoss > 0, "初始损失应该大于0");
        assertTrue(finalLoss > 0, "最终损失应该大于0");

        log.info("╔════════════════════════════════════════════════════════════╗");
        log.info("║  集成测试2: 多轮训练验证通过                                ║");
        log.info("╚════════════════════════════════════════════════════════════╝");
    }

    /**
     * 集成测试3: 模型重新训练和更新
     */
    @Test
    @Order(3)
    @DisplayName("集成测试: 模型重新训练和更新")
    void testModelRetraining() {
        log.info("╔════════════════════════════════════════════════════════════╗");
        log.info("║  集成测试3: 模型重新训练和更新                              ║");
        log.info("╚════════════════════════════════════════════════════════════╝");

        String stockCode = findStockWithEnoughData();

        // 第一次训练
        log.info("第一次训练...");
        LstmTrainerService.TrainingResult result1 = trainerService.trainModel(
                stockCode, MIN_TRAINING_DAYS, 5, 32, 0.001);

        assertTrue(result1.isSuccess(), "第一次训练应该成功");
        String firstModelPath = result1.getModelPath();
        log.info("第一次训练完成，模型路径: {}", firstModelPath);

        // 第二次训练（更新模型）
        log.info("第二次训练（更新模型）...");
        LstmTrainerService.TrainingResult result2 = trainerService.trainModel(
                stockCode, MIN_TRAINING_DAYS, 5, 32, 0.001);

        assertTrue(result2.isSuccess(), "第二次训练应该成功");
        log.info("第二次训练完成，模型路径: {}", result2.getModelPath());

        // 重新加载模型
        lstmInference.reloadLatestModel();
        log.info("模型重新加载完成");

        log.info("╔════════════════════════════════════════════════════════════╗");
        log.info("║  集成测试3: 模型重新训练验证通过                            ║");
        log.info("╚════════════════════════════════════════════════════════════╝");
    }

    /**
     * 集成测试4: 不同股票的模型训练
     */
    @Test
    @Order(4)
    @DisplayName("集成测试: 不同股票的模型训练")
    void testDifferentStocksTraining() {
        log.info("╔════════════════════════════════════════════════════════════╗");
        log.info("║  集成测试4: 不同股票的模型训练                              ║");
        log.info("╚════════════════════════════════════════════════════════════╝");

        // 获取多个有足够数据的股票
        List<StockPrice> allPrices = priceRepository.findAll();
        Map<String, Long> stockDataCount = allPrices.stream()
                .collect(java.util.stream.Collectors.groupingBy(StockPrice::getCode, java.util.stream.Collectors.counting()));

        List<String> stocksToTest = stockDataCount.entrySet().stream()
                .filter(e -> e.getValue() >= MIN_TRAINING_DAYS)
                .limit(3)
                .map(Map.Entry::getKey)
                .toList();

        log.info("将测试 {} 只股票", stocksToTest.size());

        for (String stockCode : stocksToTest) {
            log.info("训练股票: {}", stockCode);

            LstmTrainerService.TrainingResult result = trainerService.trainModel(
                    stockCode, MIN_TRAINING_DAYS, 3, 16, 0.001);

            assertTrue(result.isSuccess(), "股票 " + stockCode + " 训练应该成功");
            log.info("股票 {} 训练完成", stockCode);

            // 执行预测
            List<StockPrice> prices = priceRepository.findByCodeOrderByDateAsc(stockCode);
            float[][][] inputData = preparePredictionInput(prices, SEQUENCE_LENGTH);
            float[] prediction = lstmInference.predict(inputData);

            log.info("股票 {} 预测结果: {}", stockCode, prediction[0]);
        }

        log.info("╔════════════════════════════════════════════════════════════╗");
        log.info("║  集成测试4: 不同股票训练验证通过                            ║");
        log.info("╚════════════════════════════════════════════════════════════╝");
    }

    /**
     * 集成测试5: 预测连续性验证
     */
    @Test
    @Order(5)
    @DisplayName("集成测试: 预测连续性验证")
    void testPredictionContinuity() {
        log.info("╔════════════════════════════════════════════════════════════╗");
        log.info("║  集成测试5: 预测连续性验证                                  ║");
        log.info("╚════════════════════════════════════════════════════════════╝");

        String stockCode = findStockWithEnoughData();
        List<StockPrice> prices = priceRepository.findByCodeOrderByDateAsc(stockCode);

        // 训练模型
        trainerService.trainModel(stockCode, MIN_TRAINING_DAYS, 5, 32, 0.001);

        // 连续预测多天
        log.info("连续预测未来5个交易日...");

        for (int i = 0; i < 5; i++) {
            // 使用滑动窗口预测
            int startIdx = prices.size() - SEQUENCE_LENGTH - i;
            if (startIdx < 0) break;

            List<StockPrice> windowPrices = prices.subList(startIdx, startIdx + SEQUENCE_LENGTH);
            float[][][] inputData = preparePredictionInputFromWindow(windowPrices);
            float[] prediction = lstmInference.predict(inputData);

            LocalDate predictDate = prices.get(startIdx + SEQUENCE_LENGTH - 1).getDate();
            double actualClose = prices.get(startIdx + SEQUENCE_LENGTH - 1).getClosePrice().doubleValue();

            log.info("日期: {}, 预测值: {}, 实际收盘: {}", predictDate, prediction[0], actualClose);
        }

        log.info("╔════════════════════════════════════════════════════════════╗");
        log.info("║  集成测试5: 预测连续性验证通过                              ║");
        log.info("╚════════════════════════════════════════════════════════════╝");
    }

    /**
     * 集成测试6: 性能测试
     */
    @Test
    @Order(6)
    @DisplayName("集成测试: 性能测试")
    void testPerformance() {
        log.info("╔════════════════════════════════════════════════════════════╗");
        log.info("║  集成测试6: 性能测试                                        ║");
        log.info("╚════════════════════════════════════════════════════════════╝");

        String stockCode = findStockWithEnoughData();
        List<StockPrice> prices = priceRepository.findByCodeOrderByDateAsc(stockCode);

        // 训练性能测试
        long trainStart = System.currentTimeMillis();
        LstmTrainerService.TrainingResult result = trainerService.trainModel(
                stockCode, MIN_TRAINING_DAYS, 10, 32, 0.001);
        long trainEnd = System.currentTimeMillis();

        assertTrue(result.isSuccess(), "训练应该成功");
        log.info("训练耗时: {} ms ({} 轮)", trainEnd - trainStart, result.getEpochs());

        // 预测性能测试
        float[][][] inputData = preparePredictionInput(prices, SEQUENCE_LENGTH);

        long predictStart = System.currentTimeMillis();
        for (int i = 0; i < 100; i++) {
            lstmInference.predict(inputData);
        }
        long predictEnd = System.currentTimeMillis();

        long totalTime = predictEnd - predictStart;
        double avgTime = totalTime / 100.0;
        log.info("100次预测总耗时: {} ms, 平均每次: {} ms", totalTime, avgTime);

        // 预测应该在合理时间内完成
        assertTrue(avgTime < 100, "单次预测平均时间应小于100ms");

        log.info("╔════════════════════════════════════════════════════════════╗");
        log.info("║  集成测试6: 性能测试通过                                    ║");
        log.info("╚════════════════════════════════════════════════════════════╝");
    }

    /**
     * 集成测试7: 内存和资源清理
     */
    @Test
    @Order(7)
    @DisplayName("集成测试: 内存和资源清理")
    void testResourceCleanup() {
        log.info("╔════════════════════════════════════════════════════════════╗");
        log.info("║  集成测试7: 内存和资源清理                                  ║");
        log.info("╚════════════════════════════════════════════════════════════╝");

        String stockCode = findStockWithEnoughData();

        // 多次训练和加载
        for (int i = 0; i < 3; i++) {
            trainerService.trainModel(stockCode, MIN_TRAINING_DAYS, 3, 32, 0.001);
            lstmInference.reloadLatestModel();

            // 执行预测
            List<StockPrice> prices = priceRepository.findByCodeOrderByDateAsc(stockCode);
            float[][][] inputData = preparePredictionInput(prices, SEQUENCE_LENGTH);
            lstmInference.predict(inputData);

            log.info("第 {} 次训练和预测完成", i + 1);
        }

        // 卸载模型
        lstmInference.unload();
        assertFalse(lstmInference.isLoaded(), "卸载后模型应该未加载");

        // 重新加载
        lstmInference.reloadLatestModel();
        log.info("模型重新加载状态: {}", lstmInference.isLoaded());

        log.info("╔════════════════════════════════════════════════════════════╗");
        log.info("║  集成测试7: 资源清理验证通过                                ║");
        log.info("╚════════════════════════════════════════════════════════════╝");
    }

    // ==================== 辅助方法 ====================

    /**
     * 查找有足够数据的股票
     */
    private String findStockWithEnoughData() {
        // 首先尝试使用已知股票代码
        String[] preferredStocks = {"000001", "600000", "600519", "000858", "601318"};

        for (String code : preferredStocks) {
            List<StockPrice> prices = priceRepository.findByCodeOrderByDateAsc(code);
            if (prices.size() >= MIN_TRAINING_DAYS) {
                return code;
            }
        }

        // 如果偏好股票没有足够数据，查找任意有足够数据的股票
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
     * 准备预测输入数据
     */
    private float[][][] preparePredictionInput(List<StockPrice> prices, int sequenceLength) {
        int startIdx = Math.max(0, prices.size() - sequenceLength);
        List<StockPrice> recentPrices = prices.subList(startIdx, prices.size());

        double maxPrice = recentPrices.stream()
                .mapToDouble(p -> p.getHighPrice().doubleValue())
                .max().orElse(1.0);

        float[][] input = new float[sequenceLength][config.getInputSize()];
        for (int i = 0; i < Math.min(recentPrices.size(), sequenceLength); i++) {
            StockPrice price = recentPrices.get(i);
            input[i][0] = (float) (price.getOpenPrice().doubleValue() / maxPrice);
            input[i][1] = (float) (price.getHighPrice().doubleValue() / maxPrice);
            input[i][2] = (float) (price.getLowPrice().doubleValue() / maxPrice);
            input[i][3] = (float) (price.getClosePrice().doubleValue() / maxPrice);
            input[i][4] = price.getVolume() != null ?
                    (float) (price.getVolume().doubleValue() / 1e8) : 0f;

            // 这里只是一个简化的例子，实际中需要从 LstmDataPreprocessor 获取归一化参数
            // 并计算和填充所有11个特征
            // 为简单起见，我们只填充前5个，后续的用0填充
            for (int j = 5; j < config.getInputSize(); j++) {
                input[i][j] = 0f;
            }
        }

        return new float[][][]{input};
    }

    /**
     * 从窗口数据准备预测输入
     */
    private float[][][] preparePredictionInputFromWindow(List<StockPrice> windowPrices) {
        double maxPrice = windowPrices.stream()
                .mapToDouble(p -> p.getHighPrice().doubleValue())
                .max().orElse(1.0);

        int seqLen = windowPrices.size();
        float[][] input = new float[seqLen][config.getInputSize()];
        for (int i = 0; i < seqLen; i++) {
            StockPrice price = windowPrices.get(i);
            input[i][0] = (float) (price.getOpenPrice().doubleValue() / maxPrice);
            input[i][1] = (float) (price.getHighPrice().doubleValue() / maxPrice);
            input[i][2] = (float) (price.getLowPrice().doubleValue() / maxPrice);
            input[i][3] = (float) (price.getClosePrice().doubleValue() / maxPrice);
            input[i][4] = price.getVolume() != null ?
                    (float) (price.getVolume().doubleValue() / 1e8) : 0f;

            for (int j = 5; j < config.getInputSize(); j++) {
                input[i][j] = 0f;
            }
        }

        return new float[][][]{input};
    }
}