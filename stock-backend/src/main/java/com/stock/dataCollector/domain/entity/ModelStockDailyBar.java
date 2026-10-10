// AI_GENERATE_START --
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
 * 模型特征专用的版本化股票日线文档。
 * 与 Legacy stock_prices 隔离，完整保存换手率、量额单位、来源和不可覆盖的数据版本。
 *
 * @author mwangli
 * @since 2026-09-25
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "model_stock_daily_bars")
@CompoundIndexes({
        @CompoundIndex(name = "uk_model_daily_code_date_source_version",
                def = "{'stockCode': 1, 'tradeDate': 1, 'source': 1, 'sourceVersion': 1}", unique = true),
        @CompoundIndex(name = "idx_model_daily_source_version_date_code",
                def = "{'source': 1, 'sourceVersion': 1, 'tradeDate': 1, 'stockCode': 1}")
})
public class ModelStockDailyBar {

    /** MongoDB 文档标识。 */
    @Id
    private String id;

    /** 股票代码。 */
    private String stockCode;

    /** 交易日期。 */
    private LocalDate tradeDate;

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

    /** 当日换手率，使用数据源原始比例口径。 */
    private BigDecimal turnoverRate;

    /** 成交量单位，例如 SHARE。 */
    private String volumeUnit;

    /** 成交额单位，例如 CNY。 */
    private String amountUnit;

    /** 数据来源。 */
    private String source;

    /** 不可覆盖的数据版本。 */
    private String sourceVersion;

    /** 数据写入时间。 */
    private LocalDateTime ingestedAt;
}
// AI_GENERATE_END --
