// AI_GENERATE_START ----
package com.stock.strategyAgent.strategy.domain.dto;

import com.stock.strategyAgent.strategy.domain.entity.StrategyMode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 策略配置接口 DTO。
 * 用于在策略配置页面和策略服务之间传递可编辑的业务参数。
 *
 * @author mwangli
 * @since 2026-10-13
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StrategyConfigDto {

    /** 配置数据库主键。 */
    private Long id;
    /** 配置业务标识。 */
    private String configId;
    /** 配置版本号。 */
    private String version;
    /** 当前策略模式。 */
    private StrategyMode mode;
    /** LSTM 因子权重，范围 0 至 1。 */
    private double lstmWeight;
    /** 新闻情感因子权重，范围 0 至 1。 */
    private double sentimentWeight;
    /** 选股候选数量，范围 1 至 100。 */
    private int topN;
    /** 最低综合得分阈值，范围 0 至 1。 */
    private double minScore;
    /** 候选股票最小上市年限，单位为年；0 表示不启用。 */
    private double minimumListingYears;
    /** 移动止损因子权重，范围 0 至 1。 */
    private double trailingStopWeight;
    /** 移动止损回撤容忍度，使用小数表示比例。 */
    private double trailingStopTolerance;
    /** RSI 因子权重，范围 0 至 1。 */
    private double rsiWeight;
    /** RSI 超买阈值。 */
    private double rsiOverboughtThreshold;
    /** 成交量因子权重，范围 0 至 1。 */
    private double volumeWeight;
    /** 成交量萎缩阈值，使用小数表示比例。 */
    private double volumeShrinkThreshold;
    /** 布林带因子权重，范围 0 至 1。 */
    private double bollingerWeight;
    /** 布林带突破阈值。 */
    private double bollingerBreakoutThreshold;
    /** 高收益退出阈值。 */
    private int highReturnThreshold;
    /** 正常收益退出阈值。 */
    private int normalReturnThreshold;
    /** 低收益退出阈值。 */
    private int lowReturnThreshold;
    /** 亏损退出阈值。 */
    private int lossReturnThreshold;
    /** 移动止损指标是否启用。 */
    private boolean trailingStopEnabled;
    /** RSI 指标是否启用。 */
    private boolean rsiEnabled;
    /** 成交量指标是否启用。 */
    private boolean volumeEnabled;
    /** 布林带指标是否启用。 */
    private boolean bollingerEnabled;
    /** 策略配置是否启用。 */
    private boolean enabled;
    /** 配置更新时间，使用后端时区格式返回。 */
    private String updateTime;
}
// AI_GENERATE_END ----
