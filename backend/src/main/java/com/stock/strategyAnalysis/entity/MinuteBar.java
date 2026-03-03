package com.stock.strategyAnalysis.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 分钟K线实体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MinuteBar {
    
    /**
     * 股票代码
     */
    private String stockCode;
    
    /**
     * 时间
     */
    private LocalDateTime time;
    
    /**
     * 开盘价
     */
    private double openPrice;
    
    /**
     * 最高价
     */
    private double highPrice;
    
    /**
     * 最低价
     */
    private double lowPrice;
    
    /**
     * 收盘价
     */
    private double closePrice;
    
    /**
     * 成交量
     */
    private long volume;
    
    /**
     * 成交额
     */
    private double amount;
}