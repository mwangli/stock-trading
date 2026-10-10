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
 * 股票复权因子文档。
 * 仅保存数据源提供的因子，不在缺少数据源公式时自行推导复权价格。
 *
 * @author mwangli
 * @since 2026-09-25
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "stock_adjustment_factors")
@CompoundIndexes({
        @CompoundIndex(name = "uk_adjust_code_date_source_version",
                def = "{'stockCode': 1, 'tradeDate': 1, 'source': 1, 'sourceVersion': 1}", unique = true),
        @CompoundIndex(name = "idx_adjust_source_version_date_code",
                def = "{'source': 1, 'sourceVersion': 1, 'tradeDate': 1, 'stockCode': 1}")
})
public class StockAdjustmentFactor {

    /** MongoDB 文档标识。 */
    @Id
    private String id;

    /** 股票代码。 */
    private String stockCode;

    /** 因子对应交易日。 */
    private LocalDate tradeDate;

    /** 前复权因子。 */
    private BigDecimal forwardFactor;

    /** 后复权因子。 */
    private BigDecimal backwardFactor;

    /** 数据来源。 */
    private String source;

    /** 数据源版本或批次。 */
    private String sourceVersion;

    /** 数据写入时间。 */
    private LocalDateTime ingestedAt;
}
// AI_GENERATE_END ---
