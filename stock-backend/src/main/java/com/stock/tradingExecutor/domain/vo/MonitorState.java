package com.stock.tradingExecutor.domain.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 监控状态
 */
@Data
public class MonitorState {

    private String stockCode;
    private String direction;
    private List<PriceSample> samples;
    private BigDecimal avgPrice;
    private BigDecimal thresholdPrice;
    private LocalDateTime startTime;

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
