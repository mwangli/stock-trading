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
 * 股票单日开盘可成交性事实。
 * 分别保存买入和卖出方向状态，供 T+1 回测与真实成交偏差分析使用。
 *
 * @author mwangli
 * @since 2026-09-25
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "stock_daily_tradability_facts")
@CompoundIndexes({
        @CompoundIndex(name = "uk_tradability_code_date_source_version",
                def = "{'stockCode': 1, 'tradeDate': 1, 'source': 1, 'sourceVersion': 1}", unique = true),
        @CompoundIndex(name = "idx_tradability_source_version_date",
                def = "{'source': 1, 'sourceVersion': 1, 'tradeDate': 1, 'stockCode': 1}")
})
public class StockDailyTradabilityFact {

    /** MongoDB 文档标识。 */
    @Id
    private String id;

    /** 股票代码。 */
    private String stockCode;

    /** 交易日期。 */
    private LocalDate tradeDate;

    /** 开盘买入可执行状态。 */
    private OpenTradabilityStatus openBuyStatus;

    /** 开盘卖出可执行状态。 */
    private OpenTradabilityStatus openSellStatus;

    /** 数据源确认的开盘价格，可为空但不得用于替代模型行情。 */
    private BigDecimal observedOpenPrice;

    /** 当日涨停价格。 */
    private BigDecimal limitUpPrice;

    /** 当日跌停价格。 */
    private BigDecimal limitDownPrice;

    /** 买入方向状态说明。 */
    private String buyReason;

    /** 卖出方向状态说明。 */
    private String sellReason;

    /** 可成交性数据来源。 */
    private String source;

    /** 不可与其他版本相互覆盖的数据版本。 */
    private String sourceVersion;

    /** 数据源事实时间。 */
    private LocalDateTime capturedAt;

    /** 写入时间。 */
    private LocalDateTime ingestedAt;
}
// AI_GENERATE_END --
