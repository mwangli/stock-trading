package com.stock.dataCollector.entity;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.index.CompoundIndex;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 股票周K线数据实体
 * 存储预聚合的周K线数据
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Document(collection = "stock_prices_weekly")
@CompoundIndex(name = "idx_code_date", def = "{'code': 1, 'date': -1}", unique = true)
public class StockPriceWeekly extends StockPrice {

    /**
     * 周K线对应的起始日期（周一）
     */
    @Override
    public LocalDate getDate() {
        return super.getDate();
    }

    /**
     * 数据类型标识
     */
    private String dataType = "weekly";
}
