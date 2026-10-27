// AI_GENERATE_START------------
package com.stock.data.entity;

import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.index.CompoundIndex;

import org.springframework.data.annotation.Id;

import com.stock.data.enums.CalendarClosureType;
import com.stock.data.enums.CalendarDayStatus;
import com.stock.data.enums.CalendarVerificationStatus;
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
@Document(collection = "trade_calendar")
@CompoundIndex(name = "uk_trade_calendar_source_date",
        def = "{'market': 1, 'tradeDate': 1, 'source': 1, 'sourceVersion': 1}", unique = true)
public class TradeCalendarEntity {

    /** 数据库主键。 */
    @Id
    private String id;

    /** 市场代码，例如 CN、SH、SZ。 */
    private String market;

    /** 自然日期。 */
    private LocalDate tradeDate;

    /** 是否为交易日。 */
    private boolean tradingDay;

    /** 单日交易状态。 */
    private CalendarDayStatus dayStatus;

    /** 休市类型，开市日固定为 NONE。 */
    private CalendarClosureType closureType;

    /** 日历确认状态。 */
    private CalendarVerificationStatus verificationStatus;

    /** 前一交易日。 */
    private LocalDate previousTradingDay;

    /** 后一交易日。 */
    private LocalDate nextTradingDay;

    /** 数据来源。 */
    private String source;

    /** 数据源版本或批次。 */
    private String sourceVersion;

    /** 本次日历来源抓取时间。 */
    private LocalDateTime sourceFetchedAt;

    /** 日期被可靠来源确认的时间。 */
    private LocalDateTime confirmedAt;

    /** 数据库创建时间。 */
    private LocalDateTime createdAt;

    /** 数据库更新时间。 */
    private LocalDateTime updatedAt;
}
// AI_GENERATE_END------------
