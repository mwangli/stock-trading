// AI_GENERATE_START -
package com.stock.strategyAnalysis.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 策略模式样本外评估快照。
 * 保存候选批次实际收益形成的可审计指标，不将降级基准回测混入正式策略结论。
 *
 * @author mwangli
 * @since 2026-10-14
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "strategy_evaluation", uniqueConstraints = @UniqueConstraint(
        name = "uk_strategy_evaluation_run_mode", columnNames = {"evaluation_run_id", "strategy_mode"}))
public class StrategyEvaluationEntity {

    /** 数据库主键。 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 同一次三模式评估的运行标识。 */
    @Column(name = "evaluation_run_id", nullable = false, length = 64)
    private String evaluationRunId;

    /** 被评估的策略模式。 */
    @Column(name = "strategy_mode", nullable = false, length = 32)
    private String strategyMode;

    /** 评估状态：AVAILABLE、INSUFFICIENT_DATA 或 NOT_EVALUATED。 */
    @Column(nullable = false, length = 32)
    private String status;

    /** 指标数据来源。 */
    @Column(name = "source_type", nullable = false, length = 32)
    private String sourceType;

    /** 完整观察窗口起始日。 */
    @Column(name = "window_start")
    private LocalDate windowStart;

    /** 完整观察窗口结束日。 */
    @Column(name = "window_end")
    private LocalDate windowEnd;

    /** 样本外验证窗口起始日。 */
    @Column(name = "validation_start")
    private LocalDate validationStart;

    /** 样本外验证窗口结束日。 */
    @Column(name = "validation_end")
    private LocalDate validationEnd;

    /** 已匹配实际收益的候选数量。 */
    @Column(name = "sample_count", nullable = false)
    private int sampleCount;

    /** 已评估候选批次数量。 */
    @Column(name = "batch_count", nullable = false)
    private int batchCount;

    /** 样本外候选组合复合净收益率。 */
    @Column(name = "net_return")
    private Double netReturn;

    /** 样本外候选组合最大回撤。 */
    @Column(name = "max_drawdown")
    private Double maxDrawdown;

    /** 样本外候选组合日胜率。 */
    @Column(name = "win_rate")
    private Double winRate;

    /** 平均盈利日收益与平均亏损日收益绝对值之比。 */
    @Column(name = "profit_loss_ratio")
    private Double profitLossRatio;

    /** 基于样本外日收益离散程度计算的稳定性分数，范围为 0 至 1。 */
    @Column(name = "stability_score")
    private Double stabilityScore;

    /** 是否为按时间后段隔离的样本外结论。 */
    @Column(name = "out_of_sample", nullable = false)
    private boolean outOfSample;

    /** 候选与预测实际结果的匹配覆盖是否完整。 */
    @Column(name = "data_complete", nullable = false)
    private boolean dataComplete;

    /** 是否达到最小批次和样本量。 */
    @Column(name = "sample_sufficient", nullable = false)
    private boolean sampleSufficient;

    /** 面向页面的评估结论。 */
    @Column(nullable = false, length = 500)
    private String conclusion;

    /** 指标计算完成时间。 */
    @Column(name = "calculated_at", nullable = false)
    private LocalDateTime calculatedAt;
}
// AI_GENERATE_END -
