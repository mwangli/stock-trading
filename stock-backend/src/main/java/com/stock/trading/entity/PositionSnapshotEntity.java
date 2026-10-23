// AI_GENERATE_START------
package com.stock.trading.entity;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

import org.springframework.data.annotation.Id;

import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 券商持仓快照实体。
 * 每行代表一个快照中的持仓明细；空持仓通过 stockCode 为空的标记行表示。
 *
 * @author mwangli
 * @since 2026-09-25
 */
@Data
@Document(collection = "position_snapshot")
@CompoundIndexes({
        @CompoundIndex(name = "uk_position_snapshot_stock",
                def = "{'snapshotId': 1, 'stockCode': 1}", unique = true),
        @CompoundIndex(name = "idx_position_snapshot_account_captured",
                def = "{'brokerCode': 1, 'accountId': 1, 'capturedAt': -1}")
})
@Accessors(chain = true)
public class PositionSnapshotEntity {

    /** 数据库主键。 */
    @Id
    private String id;

    /** 全局快照编号。 */
    private String snapshotId;

    /** 券商编码。 */
    private String brokerCode;

    /** 账户哈希标识。 */
    private String accountId;

    /** 股票代码；空值表示本次快照没有持仓明细。 */
    private String stockCode;

    /** 股票名称。 */
    private String stockName;

    /** 市场或交易类别。 */
    private String market;

    /** 股东账号哈希标识。 */
    private String shareholderAccount;

    /** 总持仓数量。 */
    private Integer totalQuantity;

    /** 当前可卖数量。 */
    private Integer availableQuantity;

    /** 冻结数量。 */
    private Integer frozenQuantity;

    /** 平均成本，单位为元。 */
    private BigDecimal averageCost;

    /** 当前价格，单位为元。 */
    private BigDecimal currentPrice;

    /** 当前市值，单位为元。 */
    private BigDecimal marketValue;

    /** 首次买入日期。 */
    private LocalDate firstBuyDate;

    /** 最近买入日期。 */
    private LocalDate lastBuyDate;

    /** 券商采集时间。 */
    private LocalDateTime capturedAt;

    /** 快照来源。 */
    private String source;

    /** 脱敏原始事件引用。 */
    private String rawEventId;

    /** 记录创建时间。 */
    @CreatedDate
    private LocalDateTime createdAt;
}
// AI_GENERATE_END------
