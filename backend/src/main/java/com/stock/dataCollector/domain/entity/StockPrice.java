// AI_GENERATE_START --
package com.stock.dataCollector.domain.entity;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 股票日线价格数据实体。
 * 同时提供按股票时间序列查询和按交易日跨股票审计所需的索引。
 *
 * @author mwangli
 * @since 2026-09-25
 */
@Data
@Document(collection = "stock_prices")
@CompoundIndexes({
        @CompoundIndex(name = "idx_code_date", def = "{'code': 1, 'date': -1}", unique = true),
        @CompoundIndex(name = "idx_date_code", def = "{'date': 1, 'code': 1}")
})
public class StockPrice {

    @Id
    private String id;
    /** 股票代码 */
    private String code;
    /** 股票名称，冗余存储便于展示 */
    private String stockName;
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
// AI_GENERATE_END --
