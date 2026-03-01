package com.stock.models.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * LSTM 模型服务测试 - 简化版本
 */
class LstmTrainerServiceTest {

    @Test
    void testTrainingResultBuilder() {
        // 测试 TrainingResult 的 Builder 模式
        LstmTrainerService.TrainingResult result = LstmTrainerService.TrainingResult.builder()
            .success(true)
            .message("测试成功")
            .epochs(10)
            .trainLoss(0.05)
            .valLoss(0.06)
            .modelPath("models/test")
            .trainSamples(100)
            .valSamples(20)
            .build();

        assertTrue(result.isSuccess());
        assertEquals("测试成功", result.getMessage());
        assertEquals(10, result.getEpochs());
        assertEquals(0.05, result.getTrainLoss());
        assertEquals("models/test", result.getModelPath());
    }

    @Test
    void testTrainingStatus() {
        // 测试 TrainingStatus 类
        LstmTrainerService.TrainingStatus status = new LstmTrainerService.TrainingStatus();
        
        assertNotNull(status.getStatus());
        assertEquals(0, status.getProgress());
        assertEquals(0, status.getCurrentEpoch());
        assertEquals(0, status.getTotalEpochs());
    }

    @Test
    void testTrainingResultFailure() {
        // 测试失败情况
        LstmTrainerService.TrainingResult result = LstmTrainerService.TrainingResult.builder()
            .success(false)
            .message("训练失败：数据不足")
            .build();

        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("训练失败"));
    }
}