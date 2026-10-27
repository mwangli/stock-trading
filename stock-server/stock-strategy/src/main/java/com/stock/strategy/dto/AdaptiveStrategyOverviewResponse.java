// AI_GENERATE_START----
package com.stock.strategy.dto;

import lombok.Builder;
import lombok.Value;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 自适应策略观察与治理页面聚合响应。
 *
 * @author mwangli
 * @since 2026-10-24
 */
@Value
@Builder
public class AdaptiveStrategyOverviewResponse {

    /** 生命周期阶段。 */
    String lifecycleStage;

    /** 生命周期阶段说明。 */
    String lifecycleDescription;

    /** 当前生效策略配置版本。 */
    String strategyVersion;

    /** 当前内部策略模板模式。 */
    String currentMode;

    /** 当前策略是否启用。 */
    boolean strategyEnabled;

    /** 最近一次有效评估对应的目标交易日，可为空。 */
    LocalDate targetTradeDate;

    /** 最近一次策略演化记录时间，可为空。 */
    LocalDateTime lastEvolutionAt;

    /** 是否已紧急停止后续自动交易。 */
    boolean automaticTradingPaused;

    /** 最近一次紧急停止原因。 */
    String emergencyStopReason;

    /** 最近一次紧急停止时间。 */
    LocalDateTime emergencyStoppedAt;

    /** 最近一次紧急停止操作者。 */
    String emergencyStopOperator;

    /** 紧急停止时生效的策略版本。 */
    String emergencyStopStrategyVersion;

    /** 最近一次紧急停止执行结果。 */
    String emergencyStopResult;

    /** 决策链健康状态。 */
    List<AdaptiveStrategyHealthItem> healthItems;

    /** 生产、回滚和候选版本比较。 */
    List<AdaptiveStrategyVersionItem> versions;

    /** 策略演化时间线。 */
    List<AdaptiveStrategyEvolutionEvent> evolutionEvents;

    /** 真实交易表现摘要，可为空。 */
    AdaptiveStrategyPerformanceResponse performance;

    /** 无法确认或需要关注的异常事实。 */
    List<String> anomalies;
}
// AI_GENERATE_END----
