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
 * 市场或行业参考序列的版本化日线文档。
 * 用于计算 market_return 和 industry_return，不使用股票横截面均值冒充指数收益。
 *
 * @author mwangli
 * @since 2026-09-25
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "model_reference_daily_bars")
@CompoundIndexes({
        @CompoundIndex(name = "uk_reference_daily_type_code_date_source_version",
                def = "{'seriesType': 1, 'seriesCode': 1, 'tradeDate': 1, 'source': 1, 'sourceVersion': 1}",
                unique = true),
        @CompoundIndex(name = "idx_reference_daily_source_version_type_date",
                def = "{'source': 1, 'sourceVersion': 1, 'seriesType': 1, 'tradeDate': 1}")
})
public class ModelReferenceDailyBar {

    /** MongoDB 文档标识。 */
    @Id
    private String id;

    /** 参考序列类型。 */
    private ReferenceSeriesType seriesType;

    /** 指数或行业序列代码。 */
    private String seriesCode;

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

    /** 数据来源。 */
    private String source;

    /** 不可覆盖的数据版本。 */
    private String sourceVersion;

    /** 数据写入时间。 */
    private LocalDateTime ingestedAt;
}
// AI_GENERATE_END --
