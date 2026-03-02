package com.stock.strategyAnalysis.selector;

import com.stock.strategyAnalysis.entity.StrategyConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 得分计算器
 * 负责因子标准化和综合得分计算
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ScoreCalculator {

    /**
     * 计算综合得分
     *
     * @param lstmProbability LSTM涨的概率 [0, 1]
     * @param sentimentScore 情感得分 [-1, 1]
     * @param config 策略配置
     * @return 综合得分 [0, 1]
     */
    public double calculateTotalScore(double lstmProbability, double sentimentScore, StrategyConfig config) {
        // 因子标准化
        double lstmFactor = normalizeLstmFactor(lstmProbability);
        double sentimentFactor = normalizeSentimentFactor(sentimentScore);
        
        // 加权计算综合得分
        double totalScore = lstmFactor * config.getLstmWeight() 
                + sentimentFactor * config.getSentimentWeight();
        
        log.debug("得分计算: LSTM概率={}, 情感得分={}, LSTM因子={}, 情感因子={}, 综合得分={}",
                lstmProbability, sentimentScore, lstmFactor, sentimentFactor, totalScore);
        
        return totalScore;
    }

    /**
     * LSTM因子标准化
     * LSTM模型输出的涨跌概率本身就是 [0, 1] 区间，无需额外转换
     *
     * @param probability 涨的概率
     * @return 标准化后的因子得分 [0, 1]
     */
    public double normalizeLstmFactor(double probability) {
        // 概率本身就是 [0, 1] 区间
        // 概率 > 0.5 表示看涨，概率 < 0.5 表示看跌
        return Math.max(0, Math.min(1, probability));
    }

    /**
     * 情感因子标准化
     * 将情感得分从 [-1, 1] 映射到 [0, 1]
     *
     * @param sentimentScore 情感得分 [-1, 1]
     * @return 标准化后的因子得分 [0, 1]
     */
    public double normalizeSentimentFactor(double sentimentScore) {
        // 映射公式: (x + 1) / 2
        // -1 -> 0, 0 -> 0.5, 1 -> 1
        double normalized = (sentimentScore + 1) / 2;
        return Math.max(0, Math.min(1, normalized));
    }

    /**
     * 批量计算综合得分
     *
     * @param lstmPredictions LSTM预测结果 Map<股票代码, 涨的概率>
     * @param sentimentScores 情感得分 Map<股票代码, 情感得分>
     * @param config 策略配置
     * @return 综合得分 Map<股票代码, 综合得分>
     */
    public Map<String, Double> calculateTotalScores(
            Map<String, Double> lstmPredictions,
            Map<String, Double> sentimentScores,
            StrategyConfig config) {
        
        Map<String, Double> totalScores = new HashMap<>();
        
        // 合并所有股票代码
        lstmPredictions.keySet().forEach(code -> {
            double lstmProb = lstmPredictions.getOrDefault(code, 0.5);
            double sentiment = sentimentScores.getOrDefault(code, 0.0);
            
            double totalScore = calculateTotalScore(lstmProb, sentiment, config);
            totalScores.put(code, totalScore);
        });
        
        return totalScores;
    }

    /**
     * 计算买入信号强度
     *
     * @param rank 排名（从1开始）
     * @return 信号强度 [0-100]
     */
    public int calculateBuySignalStrength(int rank) {
        // 第1名=100, 第2名=85, 第3名=70
        // 第N名 = 100 - (N-1) × 15
        return Math.max(0, 100 - (rank - 1) * 15);
    }

    /**
     * 计算卖出信号强度（基于排名）
     *
     * @param currentRank 当前排名
     * @param thresholdRank 阈值排名（默认10）
     * @return 信号强度 [0-100]
     */
    public int calculateSellSignalStrength(int currentRank, int thresholdRank) {
        // 信号强度 = 50 + (M - 当前排名) × 5
        // 排名越靠后，卖出信号越强
        int strength = 50 + (thresholdRank - currentRank) * 5;
        return Math.max(0, Math.min(100, strength));
    }

    /**
     * 判断是否满足最低得分要求
     *
     * @param totalScore 综合得分
     * @param config 策略配置
     * @return 是否满足
     */
    public boolean meetsMinScoreRequirement(double totalScore, StrategyConfig config) {
        return totalScore >= config.getMinScore();
    }
}