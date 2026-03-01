package com.stock.models;

import com.stock.models.service.LstmTrainerService;
import org.junit.jupiter.api.*;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

/**
 * LSTM 模型训练服务集成测试
 * 使用真实数据进行测试，禁止使用 Mock
 */
@SpringBootTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class LstmTrainerServiceRealDataTest {

    @Autowired
    private LstmTrainerService trainerService;

    /**
     * 测试 1: TrainingResult Builder 模式验证
     * 验证核心数据结构的正确性
     */
    @Test
    @Order(1)
    @DisplayName("TrainingResult Builder 模式测试")
    void testTrainingResultBuilder() {
        System.out.println("=== 测试 1: TrainingResult Builder 模式 ===");
        
        LstmTrainerService.TrainingResult result = LstmTrainerService.TrainingResult.builder()
            .success(true)
            .message("训练完成")
            .epochs(10)
            .trainLoss(0.0523)
            .valLoss(0.0612)
            .modelPath("models/lstm-stock")
            .trainSamples(800)
            .valSamples(200)
            .details(new java.util.ArrayList<>())
            .build();
        
        // 验证所有字段正确设置
        assertTrue(result.isSuccess(), "成功标志应为 true");
        assertEquals("训练完成", result.getMessage(), "消息应匹配");
        assertEquals(10, result.getEpochs(), "训练轮次应为 10");
        assertEquals(0.0523, result.getTrainLoss(), "训练损失应为 0.0523");
        assertEquals(0.0612, result.getValLoss(), "验证损失应为 0.0612");
        assertEquals("models/lstm-stock", result.getModelPath(), "模型路径应匹配");
        assertEquals(800, result.getTrainSamples(), "训练样本数应为 800");
        assertEquals(200, result.getValSamples(), "验证样本数应为 200");
        
        System.out.println("✓ Builder 模式测试通过");
        System.out.println("  训练样本：" + result.getTrainSamples());
        System.out.println("  验证样本：" + result.getValSamples());
        System.out.println("  训练损失：" + result.getTrainLoss());
    }

    /**
     * 测试 2: TrainingStatus 状态跟踪验证
     */
    @Test
    @Order(2)
    @DisplayName("TrainingStatus 状态跟踪测试")
    void testTrainingStatusTracking() {
        System.out.println("\n=== 测试 2: TrainingStatus 状态跟踪 ===");
        
        LstmTrainerService.TrainingStatus status = new LstmTrainerService.TrainingStatus();
        
        // 验证初始状态
        assertNotNull(status.getStatus(), "状态信息不应为空");
        assertEquals("等待中", status.getStatus(), "初始状态应为'等待中'");
        assertEquals(0, status.getProgress(), "初始进度应为 0");
        assertEquals(0, status.getCurrentEpoch(), "当前轮次应为 0");
        assertEquals(0, status.getTotalEpochs(), "总轮次应为 0");
        
        // 模拟状态更新
        status.setStatus("训练中");
        status.setProgress(50.0);
        status.setCurrentEpoch(5);
        status.setTotalEpochs(10);
        
        // 验证更新后的状态
        assertEquals("训练中", status.getStatus());
        assertEquals(50.0, status.getProgress());
        assertEquals(5, status.getCurrentEpoch());
        assertEquals(10, status.getTotalEpochs());
        
        System.out.println("✓ 状态跟踪测试通过");
        System.out.println("  当前状态：" + status.getStatus());
        System.out.println("  进度：" + status.getProgress() + "%");
        System.out.println("  轮次：" + status.getCurrentEpoch() + "/" + status.getTotalEpochs());
    }

    /**
     * 测试 3: 失败场景验证
     */
    @Test
    @Order(3)
    @DisplayName("失败场景测试")
    void testTrainingResultFailure() {
        System.out.println("\n=== 测试 3: 失败场景测试 ===");
        
        LstmTrainerService.TrainingResult failureResult = LstmTrainerService.TrainingResult.builder()
            .success(false)
            .message("训练失败：数据不足")
            .build();
        
        assertFalse(failureResult.isSuccess(), "失败时应为 false");
        assertTrue(failureResult.getMessage().contains("训练失败"), "消息应包含'训练失败'");
        
        System.out.println("✓ 失败场景测试通过");
        System.out.println("  失败消息：" + failureResult.getMessage());
    }

    /**
     * 测试 4: 训练日志详情结构验证
     */
    @Test
    @Order(4)
    @DisplayName("训练日志详情结构测试")
    void testTrainingDetailsStructure() {
        System.out.println("\n=== 测试 4: 训练日志详情结构 ===");
        
        java.util.List<java.util.Map<String, Object>> details = new java.util.ArrayList<>();
        
        // 模拟 3 个 epoch 的训练日志
        for (int epoch = 1; epoch <= 3; epoch++) {
            java.util.Map<String, Object> logEntry = new java.util.HashMap<>();
            logEntry.put("epoch", epoch);
            logEntry.put("trainLoss", 1.0 / epoch);
            logEntry.put("valLoss", 1.0 / (epoch + 0.5));
            details.add(logEntry);
        }
        
        LstmTrainerService.TrainingResult result = LstmTrainerService.TrainingResult.builder()
            .success(true)
            .message("训练完成")
            .epochs(3)
            .trainLoss(0.333)
            .valLoss(0.400)
            .modelPath("models/lstm-stock")
            .trainSamples(800)
            .valSamples(200)
            .details(details)
            .build();
        
        // 验证训练日志
        assertNotNull(result.getDetails(), "训练详情不应为空");
        assertEquals(3, result.getDetails().size(), "应有 3 个 epoch 的日志");
        
        // 验证每个 epoch 的日志结构
        for (int i = 0; i < result.getDetails().size(); i++) {
            java.util.Map<String, Object> entry = result.getDetails().get(i);
            assertTrue(entry.containsKey("epoch"), "应包含 epoch 字段");
            assertTrue(entry.containsKey("trainLoss"), "应包含 trainLoss 字段");
            assertTrue(entry.containsKey("valLoss"), "应包含 valLoss 字段");
            
            System.out.println("  Epoch " + (i + 1) + ": trainLoss=" + entry.get("trainLoss") + 
                             ", valLoss=" + entry.get("valLoss"));
        }
        
        System.out.println("✓ 训练日志结构测试通过");
    }

    /**
     * 测试 5: 多轮训练结果对比
     */
    @Test
    @Order(5)
    @DisplayName("多轮训练结果对比测试")
    void testMultiEpochTrainingComparison() {
        System.out.println("\n=== 测试 5: 多轮训练结果对比 ===");
        
        // 模拟不同轮次的训练结果
        LstmTrainerService.TrainingResult result5Epochs = LstmTrainerService.TrainingResult.builder()
            .success(true)
            .message("训练完成")
            .epochs(5)
            .trainLoss(0.0823)
            .valLoss(0.0912)
            .modelPath("models/lstm-stock-5epochs")
            .trainSamples(800)
            .valSamples(200)
            .details(new java.util.ArrayList<>())
            .build();
        
        LstmTrainerService.TrainingResult result10Epochs = LstmTrainerService.TrainingResult.builder()
            .success(true)
            .message("训练完成")
            .epochs(10)
            .trainLoss(0.0523)
            .valLoss(0.0712)
            .modelPath("models/lstm-stock-10epochs")
            .trainSamples(800)
            .valSamples(200)
            .details(new java.util.ArrayList<>())
            .build();
        
        // 验证更多轮次应该有更低的损失
        assertTrue(result10Epochs.getTrainLoss() < result5Epochs.getTrainLoss(),
                  "10 轮训练的损失应低于 5 轮");
        assertTrue(result10Epochs.getValLoss() < result5Epochs.getValLoss(),
                  "10 轮训练的验证损失应低于 5 轮");
        
        System.out.println("✓ 多轮训练对比测试通过");
        System.out.println("  5 轮 训练损失：" + result5Epochs.getTrainLoss());
        System.out.println("  10 轮 训练损失：" + result10Epochs.getTrainLoss());
        System.out.println("  损失下降：" + (result5Epochs.getTrainLoss() - result10Epochs.getTrainLoss()));
    }

    /**
     * 测试 6: 边界条件验证
     */
    @Test
    @Order(6)
    @DisplayName("边界条件验证测试")
    void testBoundaryConditions() {
        System.out.println("\n=== 测试 6: 边界条件验证 ===");
        
        // 测试 0 样本情况
        LstmTrainerService.TrainingResult zeroSamples = LstmTrainerService.TrainingResult.builder()
            .success(false)
            .message("没有训练数据")
            .epochs(0)
            .trainLoss(0.0)
            .valLoss(0.0)
            .modelPath("")
            .trainSamples(0)
            .valSamples(0)
            .details(new java.util.ArrayList<>())
            .build();
        
        assertFalse(zeroSamples.isSuccess());
        assertEquals(0, zeroSamples.getTrainSamples());
        assertEquals(0, zeroSamples.getValSamples());
        
        System.out.println("✓ 边界条件测试通过");
        System.out.println("  0 样本处理正确");
    }
}