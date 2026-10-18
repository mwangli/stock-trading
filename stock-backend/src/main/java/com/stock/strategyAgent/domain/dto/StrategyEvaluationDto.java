// AI_GENERATE_START -----
package com.stock.strategyAgent.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 策略模式评估响应。
 *
 * @author mwangli
 * @since 2026-10-14
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StrategyEvaluationDto {

    /** 最近一次评估运行标识。 */
    private String evaluationRunId;

    /** 最近一次评估完成时间。 */
    private LocalDateTime calculatedAt;

    /** 当前生效的策略配置版本，用于和评估版本进行比对。 */
    private String currentConfigVersion;

    /** 三种策略模式的评估结果。 */
    private List<ModeEvaluationDto> modes;

    /** 页面总体提示。 */
    private String summary;

    /**
     * 单个策略模式评估结果。
     *
     * @author mwangli
     * @since 2026-10-14
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ModeEvaluationDto {

        /** 策略模式编码。 */
        private String mode;

        /** 策略模式中文名称。 */
        private String modeName;

        /** 本次模式评估涉及的策略配置版本，跨版本时使用逗号分隔展示。 */
        private String strategyConfigVersion;

        /** 本次模式评估涉及的涨跌模型版本，跨版本时使用逗号分隔展示。 */
        private String modelVersion;

        /** 本次模式评估涉及的情感模型版本，跨版本时使用逗号分隔展示。 */
        private String sentimentModelVersion;

        /** 当前生效版本是否与本次模式评估的单一策略版本一致。 */
        private boolean currentConfigVersionMatched;

        /** 评估状态。 */
        private String status;

        /** 指标数据来源。 */
        private String sourceType;

        /** 完整观察窗口起始日。 */
        private LocalDate windowStart;

        /** 完整观察窗口结束日。 */
        private LocalDate windowEnd;

        /** 样本外验证窗口起始日。 */
        private LocalDate validationStart;

        /** 样本外验证窗口结束日。 */
        private LocalDate validationEnd;

        /** 已匹配实际收益的候选数量。 */
        private int sampleCount;

        /** 已评估候选批次数量。 */
        private int batchCount;

        /** 样本外复合毛收益率，不扣除交易费用。 */
        private Double grossReturn;

        /** 样本外最大回撤。 */
        private Double maxDrawdown;

        /** 样本外日胜率。 */
        private Double winRate;

        /** 盈亏比。 */
        private Double profitLossRatio;

        /** 稳定性分数，范围为 0 至 1。 */
        private Double stabilityScore;

        /** 是否为样本外结论。 */
        private boolean outOfSample;

        /** 数据匹配是否完整。 */
        private boolean dataComplete;

        /** 样本是否充分。 */
        private boolean sampleSufficient;

        /** 评估结论。 */
        private String conclusion;
    }
}
// AI_GENERATE_END -----
