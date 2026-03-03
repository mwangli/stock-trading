package com.stock.tradingExecutor.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 价格采样
 */
@Data
public class PriceSample {
    /**
     * 股票代码
     */
    private String stockCode;
    
    /**
     * 采样价格
     */
    private BigDecimal price;
    
    /**
     * 采样时间
     */
    private LocalDateTime sampleTime;
    
    public PriceSample() {
    }
    
    public PriceSample(String stockCode, BigDecimal price) {
        this.stockCode = stockCode;
        this.price = price;
        this.sampleTime = LocalDateTime.now();
    }
}