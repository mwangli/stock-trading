// AI_GENERATE_START----
package com.stock.execution.vo;

import lombok.Data;
import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 交易账户资金与仓位状态。
 * 汇总券商账户事实，并提供风控需要的亏损率和仓位占比派生值。
 *
 * @author mwangli
 * @since 2026-10-23
 */
@Data
public class AccountStatus {

    /** 账户总资产，单位为元。 */
    private BigDecimal totalAssets;

    /** 可用资金，单位为元。 */
    private BigDecimal availableCash;

    /** 冻结资金，单位为元。 */
    private BigDecimal frozenAmount;

    /** 持仓市值，单位为元。 */
    private BigDecimal totalPosition;

    /** 当日盈亏比例，单位为百分比。 */
    private Double dailyProfitLossPercent;

    /** 当月盈亏比例，单位为百分比。 */
    private Double monthlyProfitLossPercent;

    /** 持仓占总资产比例，单位为百分比。 */
    private Double totalPositionPercent;

    /**
     * 获取当日亏损比例。
     *
     * @return 非负的当日亏损百分比，无亏损时返回 0
     */
    public Double getDailyLossPercent() {
        return dailyProfitLossPercent != null ? Math.max(0, -dailyProfitLossPercent) : 0.0;
    }

    /**
     * 获取当月亏损比例。
     *
     * @return 非负的当月亏损百分比，无亏损时返回 0
     */
    public Double getMonthlyLossPercent() {
        return monthlyProfitLossPercent != null ? Math.max(0, -monthlyProfitLossPercent) : 0.0;
    }

    /**
     * 根据总资产与持仓市值计算当前仓位比例。
     *
     * @return 持仓百分比，总资产不可用时返回 0
     */
    public Double getTotalPositionPercent() {
        if (totalAssets != null && totalAssets.compareTo(BigDecimal.ZERO) > 0 && totalPosition != null) {
            return totalPosition.divide(totalAssets, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100)).doubleValue();
        }
        return 0.0;
    }
}

// AI_GENERATE_END----
