// AI_GENERATE_START ---
package com.stock.dataCollector.domain.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 股票 5 分钟 K 线文档。
 * 每条记录保存明确的数据来源、版本和量额单位，供买卖点模型和下一根 K 线回测使用。
 *
 * @author mwangli
 * @since 2026-09-25
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "stock_price_5m")
@CompoundIndexes({
        @CompoundIndex(name = "uk_minute_code_time_source_version",
                def = "{'stockCode': 1, 'barTime': 1, 'source': 1, 'sourceVersion': 1}", unique = true),
        @CompoundIndex(name = "idx_minute_source_version_date_code_time",
                def = "{'source': 1, 'sourceVersion': 1, 'tradingDate': 1, 'stockCode': 1, 'barTime': 1}")
})
public class StockMinuteBar {

    /** MongoDB 文档标识。 */
    @Id
    private String id;

    /** 股票代码。 */
    private String stockCode;

    /** K 线起始时间。 */
    private LocalDateTime barTime;

    /** 所属交易日。 */
    private LocalDate tradingDate;

    /** 开盘价。 */
    private BigDecimal openPrice;

    /** 最高价。 */
    private BigDecimal highPrice;

    /** 最低价。 */
    private BigDecimal lowPrice;

    /** 收盘价。 */
    private BigDecimal closePrice;

    /** 成交量。 */
    private BigDecimal volume;

    /** 成交额。 */
    private BigDecimal amount;

    /** 成交量单位，例如 SHARE、LOT。 */
    private String volumeUnit;

    /** 成交额单位，例如 CNY。 */
    private String amountUnit;

    /** 数据来源。 */
    private String source;

    /** 数据源版本或批次。 */
    private String sourceVersion;

    /** 数据写入时间。 */
    private LocalDateTime ingestedAt;
}
// AI_GENERATE_END ---
