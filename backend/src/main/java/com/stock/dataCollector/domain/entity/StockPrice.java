package com.stock.dataCollector.domain.entity;

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
    private String code;
    private LocalDate date;
    private BigDecimal openPrice;
    private BigDecimal highPrice;
    private BigDecimal lowPrice;
    private BigDecimal closePrice;
    private BigDecimal volume;
    private BigDecimal amount;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
