package com.stock.dataCollector.domain.entity;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.index.CompoundIndex;

import java.time.LocalDate;

/**
 * 股票月K线数据实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Document(collection = "stock_prices_monthly")
@CompoundIndex(name = "idx_code_date", def = "{'code': 1, 'date': -1}", unique = true)
public class StockPriceMonthly extends StockPrice {

    private String dataType = "monthly";

    @Override
    public LocalDate getDate() {
        return super.getDate();
    }
}
