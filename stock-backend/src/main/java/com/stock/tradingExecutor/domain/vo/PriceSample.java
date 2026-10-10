package com.stock.tradingExecutor.domain.vo;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class PriceSample {

    private String stockCode;
    private BigDecimal price;
    private LocalDateTime sampleTime;

    public PriceSample() {}

    public PriceSample(String stockCode, BigDecimal price) {
        this.stockCode = stockCode;
        this.price = price;
        this.sampleTime = LocalDateTime.now();
    }
}
