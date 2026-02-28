package com.stock.databus.entity;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Document(collection = "stock_prices")
public class StockPrice {

    @Id
    private String id;

    private String code;

    private LocalDate date;

    private BigDecimal price1;

    private BigDecimal price2;

    private BigDecimal price3;

    private BigDecimal price4;

    private BigDecimal tradingVolume;

    private BigDecimal tradingAmount;

    private BigDecimal amplitude;

    private BigDecimal increaseRate;

    private BigDecimal changeAmount;

    private BigDecimal exchangeRate;

    private BigDecimal todayOpenPrice;

    private BigDecimal yesterdayClosePrice;

    private LocalDateTime createTime;
}
