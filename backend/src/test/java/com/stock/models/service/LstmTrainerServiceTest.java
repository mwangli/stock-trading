package com.stock.models.service;

import com.stock.models.config.LstmTrainingConfig;
import com.stock.models.dto.TrainingRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;

/**
 * LSTM 训练器服务测试
 */
@ExtendWith(MockitoExtension.class)
class LstmTrainerServiceTest {

    @Mock
    private LstmTrainingConfig config;

    @Mock
    private LstmDataPreprocessor dataPreprocessor;

    @InjectMocks
    private LstmTrainerService trainerService;

    @BeforeEach
    void setUp() {
        // 配置默认参数
        org.mockito.Mockito.when(config.getModelPath()).thenReturn("models/lstm-stock");
        org.mockito.Mockito.when(config.getSequenceLength()).thenReturn(60);
        org.mockito.Mockito.when(config.getInputSize()).thenReturn(5);
        org.mockito.Mockito.when(config.getHiddenSize()).thenReturn(50);
        org.mockito.Mockito.when(config.getNumLayers()).thenReturn(2);
        org.mockito.Mockito.when(config.getEpochs()).thenReturn(10);
        org.mockito.Mockito.when(config.getBatchSize()).thenReturn(32);
        org.mockito.Mockito.when(config.getLearningRate()).thenReturn(0.001);
        org.mockito.Mockito.when(config.getTrainRatio()).thenReturn(0.8);
    }

    @Test
    void testServiceInitialization() {
        // 验证服务初始化成功
        assertNotNull(trainerService);
    }

    @Test
    void testTrainingRequestValidation() {
        // 测试训练请求参数验证逻辑
        TrainingRequest request = TrainingRequest.builder()
                .stockCodes("600519")
                .days(365)
                .epochs(10)
                .build();

        assertNotNull(request.getStockCodes());
        assertTrue(request.getDays() >= 60);
    }

    @Test
    void testTrainingResultStructure() {
        // 测试训练结果数据结构
        LstmTrainerService.TrainingResult result = LstmTrainerService.TrainingResult.builder()
                .success(true)
                .message("测试训练完成")
                .epochs(10)
                .trainLoss(0.05)
                .valLoss(0.06)
                .modelPath("models/lstm-stock")
                .trainSamples(200)
                .valSamples(50)
                .build();

        assertTrue(result.isSuccess());
        assertEquals(10, result.getEpochs());
        assertEquals(0.05, result.getTrainLoss());
        assertEquals("models/lstm-stock", result.getModelPath());
    }
}
