package com.stock.models.inference;

import com.stock.models.config.LstmTrainingConfig;
import com.stock.models.service.LstmTrainerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * LSTM 推理服务测试
 */
@ExtendWith(MockitoExtension.class)
class LstmInferenceTest {

    @Mock
    private LstmTrainerService trainerService;

    private LstmInference lstmInference;

    @BeforeEach
    void setUp() {
        lstmInference = new LstmInference();
        // 使用ReflectionTestUtils设置mock，但由于private field，采用变通方法
        // 我们将在测试中专注公共方法的行为
    }

    @Test
    void testInitLoadsModel() {
        // Act - 模拟初始化行为
        // 验证模型加载相关属性
        assertFalse(lstmInference.isLoaded());
        assertNull(lstmInference.getLastLoadedTime());
    }
    
    @Test
    void testPredictWithUnloadedModel() {
        // Arrange: 验证模型未加载状态下行为
        float[][][] testData = new float[1][60][5]; // 1 batch, 60 timesteps, 5 features
        
        // Mock数据填充
        for(int i = 0; i < 60; i++) {
            for(int j = 0; j < 5; j++) {
                testData[0][i][j] = 0.5f;  // 示例数据
            }
        }

        // Act
        float[] result = lstmInference.predict(testData);

        // Assert  
        // 当模型未加载时，应该返回默认值
        assertNotNull(result);
        assertEquals(1, result.length);
        assertEquals(0f, result[0]);
    }

    @Test
    void testPredictWithDetails() {
        // Arrange
        String stockCode = "600519";
        float[][][] testData = new float[1][60][5]; // 1 batch, 60 timesteps, 5 features

        // Mock数据填充
        for(int i = 0; i < 60; i++) {
            for(int j = 0; j < 5; j++) {
                testData[0][i][j] = 0.5f;  // 示例数据
            }
        }

        // Act
        Map<String, Object> result = lstmInference.predictWithDetails(stockCode, testData);

        // Assert
        assertNotNull(result);
        assertEquals(stockCode, result.get("stockCode"));
        assertTrue(result.containsKey("predictedPrice"));
        assertTrue(result.containsKey("isTrained"));
        assertEquals(0.5f, result.get("confidence")); // 模型未加载时的置信度
    }

    @Test
    void testLoadAndUnloadModel() {
        // Arrange: 初始状态
        assertFalse(lstmInference.isLoaded());

        // Act: 模拟加载模型
        // 直接调用加载（虽然实际上会失败，因为我们没有物理模型）
        lstmInference.loadModel("models/non-existent"); 

        // Assert: 应仍然为false
        assertFalse(lstmInference.isLoaded());

        // Act: 卸载模型（当模型未加载时）
        lstmInference.unload();

        // Assert: 仍然是false，但没有异常
        assertFalse(lstmInference.isLoaded());
    }

    @Test
    void testModelPathGetter() {
        // Test getter方法
        String modelPath = lstmInference.getModelPath();
        assertNotNull(modelPath);
        assertTrue(modelPath.contains("models"));
    }

    @Test
    void testReloadLatestModel() {
        // Act: 调用重新加载
        lstmInference.reloadLatestModel();

        // 验证模型重新加载逻辑 - 将尝试从默认路径加载
        // 不会抛异常
        assertDoesNotThrow(() -> lstmInference.reloadLatestModel());
    }

    @Test
    void testLastLoadedTimeAfterInit() {
        // 原来构造函数会调用init()初始化
        // 直接获取上次加载时间
        LocalDateTime initialTime = lstmInference.getLastLoadedTime();

        // 由于model不存在，上次加载时间应该是null
        assertNull(initialTime);
    }
}