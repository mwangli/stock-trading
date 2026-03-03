package com.stock.dataCollector.entity;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.index.CompoundIndex;


import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 股票价格数据实体
 */
@Data
@Document(collection = "stock_prices")
@CompoundIndex(name = "idx_code_date", def = "{'code': 1, 'date': -1}", unique = true)

public class StockPrice {

    @Id
    private String id;

    /**
     * 股票代码
     */
    private String code;
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

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 修改时间
     */
    private LocalDateTime updateTime;
}