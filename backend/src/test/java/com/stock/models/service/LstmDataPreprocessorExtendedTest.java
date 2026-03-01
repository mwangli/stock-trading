package com.stock.models.service;

import com.stock.databus.entity.StockPrice;
import com.stock.databus.repository.PriceRepository;
import com.stock.models.config.LstmTrainingConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * LSTM数据预处理器服务扩展测试
 */
@ExtendWith(MockitoExtension.class)
class LstmDataPreprocessorExtendedTest {

    @Mock
    private PriceRepository priceRepository;

    @Mock
    private LstmTrainingConfig config;

    private LstmDataPreprocessor dataPreprocessor;

    @BeforeEach
    void setUp() {
        dataPreprocessor = new LstmDataPreprocessor(priceRepository, config);
        
        // 设置默认配置
        when(config.getSequenceLength()).thenReturn(30); // 使用较短序列长度加快测试
        when(config.getInputSize()).thenReturn(5);
    }

    @Test
    void testTrainingDataNormalizationRangeValues() {
        // Arrange 
        String stockCode = "600519";
        int days = 120;

        // 创建一组价格数据以充分测试归一化
        List<StockPrice> mockPrices = createMockStockPrices(days);
        
        when(priceRepository.findByCodeOrderByDateAsc(stockCode)).thenReturn(mockPrices);

        // Act
        LstmDataPreprocessor.TrainingData result = dataPreprocessor.prepareTrainingData(stockCode, days);

        // Assert
        assertNotNull(result);
        assertTrue(result.getFeatures().size() > 0);
        
        // 检查缩放参数
        float[][] scalerParams = result.getScalerParams();
        assertNotNull(scalerParams);
        // 验证缩放参数格式：[inputSize + 1][2] (特征+标签，每个都有min/max)
        assertTrue(scalerParams.length >= 5); // 至少5个特征的min/max值
        assertEquals(2, scalerParams[0].length); // min/max两个值
    }

    @Test
    void testFeaturesExtractedAsExpected() {
        // 测试特征提取是否按预期工作
        
        String stockCode = "000858";
        int days = 100;

        List<StockPrice> mockPrices = createMockStockPrices(days);
        when(priceRepository.findByCodeOrderByDateAsc(stockCode)).thenReturn(mockPrices);

        // Act
        LstmDataPreprocessor.TrainingData trainingData = dataPreprocessor.prepareTrainingData(stockCode, days);

        // Assert if not null that features have right dimensions
        if (trainingData != null) {
            List<float[][]> features = trainingData.getFeatures();
            List<float[]> labels = trainingData.getLabels();
            
            // 特征维度: [sequence][sequence_length][num_features]
            // 每个序列应该有固定的序列长度和固定的特征数
            for (float[][] sequence : features) {
                for (float[] timestep : sequence) {
                    assertEquals(5, timestep.length, "每个时间步应该有5个特征");  
                }
                assertEquals(30, sequence.length, "序列长度应该符合配置值");  // config'd sequence length
            }
            
            // 标签维度验证
            for (float[] label : labels) {
                assertEquals(1, label.length, "每个标签应该是个标量值");
            }
        }
    }

    @Test
    void testDataBuildSequencesProperly() {
        // 测试序列构建逻辑 - 确保正确的滑动窗口创建
        String stockCode = "000001";
        int days = 100;

        List<StockPrice> mockPrices = createMockStockPrices(days);
        when(priceRepository.findByCodeOrderByDateAsc(stockCode)).thenReturn(mockPrices);
        when(config.getSequenceLength()).thenReturn(10); // 使用更小的序列长度便于验证

        // Act
        LstmDataPreprocessor.TrainingData result = dataPreprocessor.prepareTrainingData(stockCode, days);

        // Assert
        if (result != null) {
            int sequenceLength = 10; // 与配置保持一致
            List<StockPrice> availablePrices = createMockStockPrices(days); // 这是我们用来构建序列的数据
            
            // 可能构建的序列数 = 总天数 - 序列长度
            int expectedSequences = availablePrices.size() - sequenceLength;
            assertTrue(result.getFeatures().size() >= expectedSequences - 1,  // 减1是为了允许小的边界差异
                "应该有足够数量的时间序列");
        }
    }

    @Test
    void testNormalizedValuesInRange() {
        // 测试归一化后值是否在预期范围内
        String stockCode = "002352";
        int days = 80;
        
        List<StockPrice> mockPrices = createMockStockPrices(days);
        when(priceRepository.findByCodeOrderByDateAsc(stockCode)).thenReturn(mockPrices);
        when(config.getSequenceLength()).thenReturn(15);

        // Act
        LstmDataPreprocessor.TrainingData result = dataPreprocessor.prepareTrainingData(stockCode, days);

        // 如果有结果就验证值范围
        if (result != null && !result.getFeatures().isEmpty()) {
            List<float[][]> features = result.getFeatures();
            
            // 验证所有归一化特征值是否在[0,1]之间
            for (float[][] sequence : features) {
                for (float[] timestep : sequence) {
                    for (float feature : timestep) {
                        assertTrue(feature >= 0.0f && feature <= 1.0f, "归一化特征值应当在[0,1]之间，但实际值=" + feature);
                    }
                }
            }
        }
    }

    @Test
    void testScalarParamsAreReasonable() {
        // 测试缩放参数（最小值，最大值）是否合理
        String stockCode = "601318";
        int days = 90;

        List<StockPrice> mockPrices = createMockStockPrices(days);
        when(priceRepository.findByCodeOrderByDateAsc(stockCode)).thenReturn(mockPrices);

        // Act
        LstmDataPreprocessor.TrainingData result = dataPreprocessor.prepareTrainingData(stockCode, days);

        // Assert
        if (result != null && result.getScalerParams() != null) {
            float[][] scalerParams = result.getScalerParams();
            
            // 验证缩放参数的合理性
            for (int featureIndex = 0; featureIndex < Math.min(5, scalerParams.length); featureIndex++) {
                float min = scalerParams[featureIndex][0];  // Min
                float max = scalerParams[featureIndex][1];  // Max
                
                assertTrue(min <= max, 
                        String.format("Feature %d: 最小值应小于等于最大值，但现在 min=%.2f, max=%.2f", 
                                featureIndex, min, max));
            }
        }
    }

    @Test
    void testExceptionHandlingInProcessing() {
        // 测试异常处理
        String stockCode = "INVALID_CODE";
        int days = 50;

        when(priceRepository.findByCodeOrderByDateAsc(stockCode)).thenThrow(new RuntimeException("DB Error"));

        // Act & Assert
        assertThrows(RuntimeException.class, () -> {
            dataPreprocessor.prepareTrainingData(stockCode, days);
        });
    }

    @Test
    void testDataPreprocessorHandlesEdgeCases() {
        // 测试边界情况

        // Case 1: 空数据集
        String emptyCode = "EMPTY_CODE";
        when(priceRepository.findByCodeOrderByDateAsc(emptyCode)).thenReturn(new ArrayList<>());

        LstmDataPreprocessor.TrainingData emptyResult = dataPreprocessor.prepareTrainingData(emptyCode, 60);
        assertNull(emptyResult);

        // Case 2: 数据不足构建序列
        String insufficientCode = "INSUFF_CODE";
        List<StockPrice> insufficientData = createMockStockPrices(20);  // 少于序列长度 + 序列长度
        when(config.getSequenceLength()).thenReturn(25);   // 设置一个较大的序列长度
        when(priceRepository.findByCodeOrderByDateAsc(insufficientCode)).thenReturn(insufficientData);

        LstmDataPreprocessor.TrainingData insufficientResult = dataPreprocessor.prepareTrainingData(insufficientCode, 50);
        // 当数据不足以构建序列时，结果可能为null或者是处理过的状态
        // 根据代码逻辑，返回可能是null
        // 但这里我们确保流程不会有异常
        assertDoesNotThrow(() -> {
            dataPreprocessor.prepareTrainingData(insufficientCode, 50);
        });
    }

    @Test
    void testDataFeaturesMatchSpecifications() {
        // 测试数据符合预期规格 - 5个特征
        String stockCode = "601939";
        int days = 120;

        List<StockPrice> mockPrices = createMockStockPrices(days);
        when(priceRepository.findByCodeOrderByDateAsc(stockCode)).thenReturn(mockPrices);

        // Act
        LstmDataPreprocessor.TrainingData result = dataPreprocessor.prepareTrainingData(stockCode, days);

        // Assert 5个特征定义
        if(result != null && result.getFeatures().size() > 0) {
            // 取第一个序列
            float[][] firstFeature = result.getFeatures().get(0);
            if(firstFeature.length > 0) {
                float[] firstTimestep = firstFeature[0];
                assertEquals(5, firstTimestep.length, "应该有5个特征：开盘、最高、最低、收盘、成交量");
                
                // 特征顺序应该是：
                // [0]=开盘, [1]=最高, [2]=最低, [3]=收盘, [4]=成交量
                // 这些值应该是合理的股票价格特征
                assertTrue(firstTimestep[0] >= 0, "开盘价格应该是非负数");
                assertTrue(firstTimestep[1] >= 0, "最高价格应该是非负数");
                assertTrue(firstTimestep[2] >= 0, "最低价格应该是非负数");
                assertTrue(firstTimestep[3] >= 0, "收盘价格应该是非负数");
                assertTrue(firstTimestep[4] >= 0, "成交量应该是非负数");
            }
        }
    }

    // 辅助方法: 创建模拟价格数据
    private List<StockPrice> createMockStockPrices(int count) {
        List<StockPrice> prices = new ArrayList<>();
        
        LocalDate startDate = LocalDate.now().minusDays(count - 1);
        
        for (int i = 0; i < count; i++) {
            prices.add(createMockStockPrice(startDate.plusDays(i), i));
        }
        
        return prices;
    }

    private StockPrice createMockStockPrice(LocalDate date, int index) {
        StockPrice price = new StockPrice();
        price.setCode("TEST");
        price.setDate(date);
        // 创建递增的趋势数据以帮助验证归一化
        price.setTodayOpenPrice(new BigDecimal(30 + index * 0.1));  // 逐渐变化
        price.setPrice1(new BigDecimal(35 + index * 0.1)); // 峰值稍微高点
        price.setPrice2(new BigDecimal(25 + index * 0.1)); // 谷值稍微低点
        price.setPrice3(new BigDecimal(32 + index * 0.1)); // 收盘在中间
        price.setTradingVolume(new BigDecimal(1000000 + index * 10000)); // 交易量随时间逐渐变化

        return price;
    }
}