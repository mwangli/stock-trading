// AI_GENERATE_START-------
package com.stock.decision.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 策略得分 VO
 *
 * @author mwangli
 * @since 2026-10-23
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StrategyScore {

    /**
     * 股票代码。
     */
    private String stockCode;
    /**
     * 是否触发移动止损条件。
     */
    private boolean trailingStopTriggered;
    /**
     * RSI 指标值。
     */
    private double rsiValue;
    /**
     * 成交量比率。
     */
    private double volumeRatio;
    /**
     * 是否突破布林带阈值。
     */
    private boolean bollingerBreakout;
    /**
     * 策略计算得到的综合得分。
     */
    private int totalScore;
    /**
     * 策略当前采用的动态阈值。
     */
    private int dynamicThreshold;
    /**
     * 策略得分计算时间。
     */
    private LocalDateTime calculateTime;
}
// AI_GENERATE_END-------
