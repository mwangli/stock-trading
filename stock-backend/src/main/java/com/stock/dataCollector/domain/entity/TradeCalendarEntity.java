// AI_GENERATE_START --
package com.stock.dataCollector.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 证券市场交易日历实体。
 * 用于区分市场休市、个股停牌和行情缺失，并提供前后交易日关系。
 *
 * @author mwangli
 * @since 2026-09-25
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "trade_calendar")
public class TradeCalendarEntity {

    /** 数据库主键。 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 市场代码，例如 CN、SH、SZ。 */
    @Column(nullable = false, length = 16)
    private String market;

    /** 自然日期。 */
    @Column(name = "trade_date", nullable = false)
    private LocalDate tradeDate;

    /** 是否为交易日。 */
    @Column(name = "trading_day", nullable = false)
    private boolean tradingDay;

    /** 前一交易日。 */
    @Column(name = "previous_trading_day")
    private LocalDate previousTradingDay;

    /** 后一交易日。 */
    @Column(name = "next_trading_day")
    private LocalDate nextTradingDay;

    /** 数据来源。 */
    @Column(nullable = false, length = 64)
    private String source;

    /** 数据源版本或批次。 */
    @Column(name = "source_version", nullable = false, length = 128)
    private String sourceVersion;

    /** 数据库创建时间。 */
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    /** 数据库更新时间。 */
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
// AI_GENERATE_END --
