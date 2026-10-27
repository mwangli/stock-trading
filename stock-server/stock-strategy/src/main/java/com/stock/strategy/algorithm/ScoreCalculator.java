// AI_GENERATE_START--------
package com.stock.strategy.algorithm;

import com.stock.strategy.entity.StrategyConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 得分计算器。
 * 负责 LSTM 因子和可交易新闻影响因子的标准化与综合得分计算。
 *
 * @author mwangli
 * @since 2026-10-18
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ScoreCalculator {

    /**
     * 计算综合得分
     *
     * @param lstmProbability LSTM涨的概率 [0, 1]
     * @param newsImpactScore 新闻影响得分 [-1, 1]；为空表示没有可交易新闻影响
     * @param config 策略配置
     * @return 综合得分 [0, 1]
     */
    public double calculateTotalScore(double lstmProbability, Double newsImpactScore, StrategyConfig config) {
        double lstmFactor = normalizeLstmFactor(lstmProbability);
        if (newsImpactScore == null || Math.abs(newsImpactScore) < 0.000001D) {
            return lstmFactor;
        }
        double newsFactor = normalizeNewsImpactFactor(newsImpactScore);

        double totalScore = lstmFactor * config.getLstmWeight()
                + newsFactor * config.getSentimentWeight();

        log.debug("得分计算: LSTM概率={}, 新闻影响得分={}, LSTM因子={}, 新闻因子={}, 综合得分={}",
                lstmProbability, newsImpactScore, lstmFactor, newsFactor, totalScore);

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
     * 新闻影响因子标准化。
     * 只有存在可交易新闻影响时才调用，避免中性新闻注入固定基线分。
     *
     * @param newsImpactScore 新闻影响得分 [-1, 1]
     * @return 标准化后的因子得分 [0, 1]
     */
    public double normalizeNewsImpactFactor(double newsImpactScore) {
        double normalized = (newsImpactScore + 1) / 2;
        return Math.max(0, Math.min(1, normalized));
    }

    /**
     * 批量计算综合得分
     *
     * @param lstmPredictions LSTM预测结果 Map<股票代码, 涨的概率>
     * @param newsImpactScores 可交易新闻影响得分 Map<股票代码, 新闻影响得分>
     * @param config 策略配置
     * @return 综合得分 Map<股票代码, 综合得分>
     */
    public Map<String, Double> calculateTotalScores(
            Map<String, Double> lstmPredictions,
            Map<String, Double> newsImpactScores,
            StrategyConfig config) {

        Map<String, Double> totalScores = new HashMap<>();

        // 合并所有股票代码
        lstmPredictions.keySet().forEach(code -> {
            double lstmProb = lstmPredictions.getOrDefault(code, 0.5);
            Double newsImpact = newsImpactScores.get(code);

            double totalScore = calculateTotalScore(lstmProb, newsImpact, config);
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
// AI_GENERATE_END--------
