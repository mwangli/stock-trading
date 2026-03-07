package com.stock.strategyAnalysis.selector;

import com.stock.strategyAnalysis.entity.StrategyConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ScoreCalculator 测试类
 */
class ScoreCalculatorTest {

    private ScoreCalculator scoreCalculator;

    @BeforeEach
    void setUp() {
        scoreCalculator = new ScoreCalculator();
    }

    @Test
    void testNormalizeLstmFactor() {
        // 测试 LSTM 因子标准化
        assertEquals(0.5, scoreCalculator.normalizeLstmFactor(0.5), 0.001);
        assertEquals(0.8, scoreCalculator.normalizeLstmFactor(0.8), 0.001);
        assertEquals(0.0, scoreCalculator.normalizeLstmFactor(0.0), 0.001);
        assertEquals(1.0, scoreCalculator.normalizeLstmFactor(1.0), 0.001);
    }

    @Test
    void testNormalizeSentimentFactor() {
        // 测试情感因子标准化
        // -1 -> 0
        assertEquals(0.0, scoreCalculator.normalizeSentimentFactor(-1.0), 0.001);
        // 0 -> 0.5
        assertEquals(0.5, scoreCalculator.normalizeSentimentFactor(0.0), 0.001);
        // 1 -> 1
        assertEquals(1.0, scoreCalculator.normalizeSentimentFactor(1.0), 0.001);
    }

//    @Test
//    void testCalculateTotalScore() {
//        StrategyConfig config = StrategyConfig.defaultConfig();
//
//        // 测试综合得分计算
//        double totalScore = scoreCalculator.calculateTotalScore(0.7, 0.5, config);
//
//        // LSTM: 0.7 * 0.6 = 0.42
//        // 情感: 0.75 * 0.4 = 0.3 (情感0.5归一化后为0.75)
//        // 总分: 约 0.72
//        assertTrue(totalScore > 0.6 && totalScore < 0.8);
//    }

    @Test
    void testCalculateBuySignalStrength() {
        // 第1名 = 100
        assertEquals(100, scoreCalculator.calculateBuySignalStrength(1));
        // 第2名 = 85
        assertEquals(85, scoreCalculator.calculateBuySignalStrength(2));
        // 第3名 = 70
        assertEquals(70, scoreCalculator.calculateBuySignalStrength(3));
    }

    @Test
    void testCalculateSellSignalStrength() {
        // 排名15，阈值10，强度 = 50 + (10-15)*5 = 25
        assertEquals(25, scoreCalculator.calculateSellSignalStrength(15, 10));
        // 排名20，阈值10，强度 = 50 + (10-20)*5 = 0
        assertEquals(0, scoreCalculator.calculateSellSignalStrength(20, 10));
    }
}