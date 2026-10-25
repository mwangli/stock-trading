// AI_GENERATE_START --
package com.stock.agent.tooling.model;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 自适应策略生命周期只读事实。
 *
 * @param lifecycleStage 生命周期阶段
 * @param lifecycleDescription 生命周期说明
 * @param strategyVersion 当前策略版本
 * @param currentMode 当前策略模式
 * @param strategyEnabled 策略是否启用
 * @param targetTradeDate 最近目标交易日
 * @param lastEvolutionAt 最近演化时间
 * @param automaticTradingPaused 是否紧急停止后续自动交易
 * @param healthItems 决策链健康摘要
 * @param versions 模型版本摘要
 * @param evolutionEvents 演化事件摘要
 * @param anomalies 异常事实
 * @param asOf 查询时间
 * @author mwangli
 * @since 2026-10-25
 */
public record AdaptiveStrategyFact(
        String lifecycleStage, String lifecycleDescription, String strategyVersion,
        String currentMode, boolean strategyEnabled, LocalDate targetTradeDate,
        LocalDateTime lastEvolutionAt, boolean automaticTradingPaused,
        List<String> healthItems, List<String> versions, List<String> evolutionEvents,
        List<String> anomalies, LocalDateTime asOf) {
}
// AI_GENERATE_END --
