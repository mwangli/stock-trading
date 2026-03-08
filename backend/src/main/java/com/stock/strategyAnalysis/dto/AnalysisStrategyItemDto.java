package com.stock.strategyAnalysis.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 分析页策略项 DTO
 * 用于「策略分析中心」页面的策略列表展示与开关状态
 *
 * @author AI Assistant
 * @since 1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnalysisStrategyItemDto {

    /**
     * 策略唯一标识，与前端约定一致
     */
    private String id;

    /**
     * 类型：选股(selection) 或 交易(trading)
     */
    private String type;

    /**
     * 前端 i18n 的 key，如 strategyAnalysis.selection.doubleFactor
     */
    private String nameKey;

    /**
     * 是否启用
     */
    private boolean active;

    /**
     * 胜率 (0–100)，占位用
     */
    private double winRate;

    /**
     * 累计盈亏（元），占位用
     */
    private double pnl;

    /**
     * 交易次数，占位用
     */
    private int totalTrades;
}
