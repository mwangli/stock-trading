// AI_GENERATE_START ----
package com.stock.model.service;

import com.stock.model.config.LstmDataQualityConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 模型候选与双模型组合的统一质量评分策略。
 *
 * @author mwangli
 * @since 2026-10-24
 */
@Component
@RequiredArgsConstructor
public class ModelPromotionPolicy {

    private final LstmDataQualityConfig dataQualityConfig;

    /**
     * 计算模型综合质量分。
     *
     * @param rankIc 排名相关系数
     * @param netReturn 扣成本收益率
     * @param maximumDrawdown 最大回撤率
     * @return 范围约为 -0.8 到 1 的综合质量分
     */
    public double qualityScore(double rankIc, double netReturn, double maximumDrawdown) {
        requireFinite("Rank IC", rankIc);
        requireFinite("扣成本收益", netReturn);
        requireFinite("最大回撤", maximumDrawdown);
        double rankIcScore = clamp(rankIc / 0.05D, -1D, 1D);
        double returnScore = clamp(netReturn / 0.02D, -1D, 1D);
        double drawdownScore = 1D - clamp(maximumDrawdown / dataQualityConfig.getMaximumDrawdown(), 0D, 1D);
        return 0.45D * rankIcScore + 0.35D * returnScore + 0.20D * drawdownScore;
    }

    /**
     * 校验候选模型必要的收益和回撤硬门禁。
     *
     * @param netReturn 扣成本收益率
     * @param maximumDrawdown 最大回撤率
     * @throws IllegalStateException 指标无效、收益不为正或回撤越界时抛出
     */
    public void requirePerformanceHardGate(double netReturn, double maximumDrawdown) {
        requireFinite("扣成本收益", netReturn);
        requireFinite("最大回撤", maximumDrawdown);
        if (netReturn <= dataQualityConfig.getMinimumNetReturn()) {
            throw new IllegalStateException("候选模型扣成本收益必须大于 "
                    + dataQualityConfig.getMinimumNetReturn() + ": " + netReturn);
        }
        if (maximumDrawdown > dataQualityConfig.getMaximumDrawdown()) {
            throw new IllegalStateException("候选模型最大回撤超过阈值: "
                    + maximumDrawdown + " > " + dataQualityConfig.getMaximumDrawdown());
        }
    }

    /**
     * 判断滚动绩效是否满足统一硬门禁。
     *
     * @param netReturn 扣成本收益率
     * @param maximumDrawdown 最大回撤率
     * @return 指标有限、收益为正且回撤未越界时返回 true
     */
    public boolean passesPerformanceHardGate(double netReturn, double maximumDrawdown) {
        return Double.isFinite(netReturn)
                && Double.isFinite(maximumDrawdown)
                && netReturn > dataQualityConfig.getMinimumNetReturn()
                && maximumDrawdown <= dataQualityConfig.getMaximumDrawdown();
    }

    /**
     * 获取自动晋级所需的最低综合质量分优势。
     *
     * @return 最低综合质量分优势
     */
    public double minimumQualityScoreAdvantage() {
        return dataQualityConfig.getMinimumQualityScoreAdvantage();
    }

    /**
     * 判断候选模型是否达到自动晋级要求。
     *
     * @param candidateScore 候选模型综合质量分
     * @param activeScore 当前生产模型综合质量分，无生产模型时为空
     * @return 是否应自动晋级
     */
    public boolean shouldPromote(double candidateScore, Double activeScore) {
        requireFinite("候选综合质量分", candidateScore);
        if (activeScore == null) {
            return candidateScore >= dataQualityConfig.getMinimumInitialQualityScore();
        }
        requireFinite("生产综合质量分", activeScore);
        return candidateScore >= activeScore + dataQualityConfig.getMinimumQualityScoreAdvantage();
    }

    private void requireFinite(String metricName, double value) {
        if (!Double.isFinite(value)) {
            throw new IllegalStateException(metricName + "不是有效数值: " + value);
        }
    }

    private double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
// AI_GENERATE_END ----
