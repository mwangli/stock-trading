package com.stock.databus.entity;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 股票价格数据实体
 */
@Data
@Document(collection = "stock_prices")
public class StockPrice {

    @Id
    private String id;

    /**
     * 股票代码
     */
    private String code;

    /**
     * 股票名称
     */
    private String name;

    /**
     * 交易日期
     */
    private LocalDate date;

    /**
     * 开盘价
     */
    private BigDecimal openPrice;

    /**
     * 最高价
     */
    private BigDecimal highPrice;

    /**
     * 最低价
     */
    private BigDecimal lowPrice;

    /**
     * 收盘价
     */
    private BigDecimal closePrice;

    /**
     * 成交量（手）
     */
    private BigDecimal volume;

    /**
     * 成交额（元）
     */
    private BigDecimal amount;
}