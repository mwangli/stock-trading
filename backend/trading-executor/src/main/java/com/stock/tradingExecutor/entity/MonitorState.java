package com.stock.tradingExecutor.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 监控状态
 */
@Data
public class MonitorState {
    /**
     * 股票代码
     */
    private String stockCode;
    
    /**
     * 买卖方向
     */
    private String direction;
    
    /**
     * 价格采样列表
     */
    private List<PriceSample> samples;
    
    /**
     * 平均价格
     */
    private BigDecimal avgPrice;
    
    /**
     * 触发阈值价格
     */
    private BigDecimal thresholdPrice;
    
    /**
     * 开始时间
     */
    private LocalDateTime startTime;
    
    /**
     * 计算平均价格
     */
    public BigDecimal calculateAvgPrice() {
        if (samples == null || samples.isEmpty()) {
            return BigDecimal.ZERO;
        }
        BigDecimal sum = samples.stream()
                .map(PriceSample::getPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return sum.divide(BigDecimal.valueOf(samples.size()), 2, BigDecimal.ROUND_HALF_UP);
    }
}