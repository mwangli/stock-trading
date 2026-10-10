package com.stock.tradingExecutor.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 风控参数配置
 */
@Data
@Component
@ConfigurationProperties(prefix = "trading.risk")
public class RiskConfig {
    /**
     * 日亏损限制 (%)
     */
    private Double maxDailyLoss = 3.0;
    
    /**
     * 月亏损限制 (%)
     */
    private Double maxMonthlyLoss = 10.0;
    
    /**
     * 单股仓位上限 (%)
     */
    private Double maxSinglePosition = 30.0;
    
    /**
     * 总仓位上限 (%)
     */
    private Double maxTotalPosition = 80.0;
    
    /**
     * 最低仓位 (%)
     */
    private Double minPosition = 20.0;
    
    /**
     * 行业集中度上限 (%)
     */
    private Double maxIndustryConcentration = 50.0;
    
    /**
     * 个股止损比例 (%)
     */
    private Double singleStockStopLoss = 8.0;
    
    /**
     * 总止损比例 (%)
     */
    private Double totalStopLoss = 15.0;
    
    /**
     * 单笔最小金额
     */
    private Double minOrderAmount = 1000.0;
    
    /**
     * 单日交易次数上限
     */
    private Integer maxDailyTrades = 10;
}