// AI_GENERATE_START------
package com.stock.execution.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 监控状态
 *
 * @author mwangli
 * @since 2026-10-23
 */
@Data
public class MonitorState {

    /**
     * 股票代码。
     */
    private String stockCode;
    /**
     * 监控的交易方向。
     */
    private String direction;
    /**
     * 已采集的价格样本列表。
     */
    private List<PriceSample> samples;
    /**
     * 价格样本平均价格，单位为元。
     */
    private BigDecimal avgPrice;
    /**
     * 触发监控动作的阈值价格，单位为元。
     */
    private BigDecimal thresholdPrice;
    /**
     * 监控开始时间。
     */
    private LocalDateTime startTime;

    /**
     * 计算当前价格样本的平均价格。
     *
     * @return 平均采样价格，无样本时返回 0
     */
    public BigDecimal calculateAvgPrice() {
        if (samples == null || samples.isEmpty()) {
            return BigDecimal.ZERO;
        }
        BigDecimal sum = samples.stream()
                .map(PriceSample::getPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return sum.divide(BigDecimal.valueOf(samples.size()), 2, RoundingMode.HALF_UP);
    }
}

// AI_GENERATE_END------
