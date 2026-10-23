// AI_GENERATE_START---
package com.stock.trading.vo;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 股票价格监控采样值。
 * 保存指定股票在某一时刻的实时价格，用于短周期价格判断。
 *
 * @author mwangli
 * @since 2026-10-23
 */
@Data
public class PriceSample {

    /** 六位股票代码。 */
    private String stockCode;

    /** 采样价格，单位为元。 */
    private BigDecimal price;

    /** 采样时间。 */
    private LocalDateTime sampleTime;

    /** 创建空价格采样对象。 */
    public PriceSample() {}

    /**
     * 创建当前时刻的价格采样对象。
     *
     * @param stockCode 六位股票代码
     * @param price 采样价格，单位为元
     */
    public PriceSample(String stockCode, BigDecimal price) {
        this.stockCode = stockCode;
        this.price = price;
        this.sampleTime = LocalDateTime.now();
    }
}

// AI_GENERATE_END---
