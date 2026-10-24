// AI_GENERATE_START-------
package com.stock.market.entity;

import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.index.CompoundIndex;

import org.springframework.data.annotation.Id;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 历史时点股票池实体。
 * 保存股票上市、退市、ST、板块和行业状态的有效期，避免回测使用当前股票池产生幸存者偏差。
 *
 * @author mwangli
 * @since 2026-09-25
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "historical_stock_universe")
@CompoundIndex(name = "uk_historical_universe_version",
        def = "{'stockCode': 1, 'effectiveFrom': 1, 'source': 1, 'sourceVersion': 1}", unique = true)
public class HistoricalStockUniverseEntity {

    /** 数据库主键。 */
    @Id
    private String id;

    /** 股票代码。 */
    private String stockCode;

    /** 股票名称。 */
    private String stockName;

    /** 交易市场。 */
    private String market;

    /** 历史板块代码。 */
    private String boardCode;

    /** 历史行业代码。 */
    private String industryCode;

    /** 上市日期。 */
    private LocalDate listedDate;

    /** 退市日期。 */
    private LocalDate delistedDate;

    /** ST 状态，例如 NORMAL、ST、STAR_ST、DELISTING。 */
    private String stStatus;

    /** 该版本开始生效日期。 */
    private LocalDate effectiveFrom;

    /** 该版本失效日期；为空表示仍有效。 */
    private LocalDate effectiveTo;

    /** 数据来源。 */
    private String source;

    /** 数据源版本或批次。 */
    private String sourceVersion;

    /** 数据源采集时间。 */
    private LocalDateTime capturedAt;

    /** 数据库创建时间。 */
    private LocalDateTime createdAt;

    /** 数据库更新时间。 */
    private LocalDateTime updatedAt;
}
// AI_GENERATE_END-------
