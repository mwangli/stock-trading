package com.stock.dataCollector.domain.entity;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.index.CompoundIndex;

import java.time.LocalDate;

/**
 * 股票周K线数据实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Document(collection = "stock_prices_weekly")
@CompoundIndex(name = "idx_code_date", def = "{'code': 1, 'date': -1}", unique = true)
public class StockPriceWeekly extends StockPrice {

    private String dataType = "weekly";

    @Override
    public LocalDate getDate() {
        return super.getDate();
    }
}
