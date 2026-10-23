// AI_GENERATE_START-----
package com.stock.trading.entity;

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
@Accessors(chain = true)
public class PositionSnapshotEntity {

    /** 数据库主键。 */
    @Id
    private Long id;

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
    private LocalDateTime createdAt;

    /**
     * 初始化创建时间。
     */
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        totalQuantity = totalQuantity != null ? totalQuantity : 0;
        availableQuantity = availableQuantity != null ? availableQuantity : 0;
        frozenQuantity = frozenQuantity != null ? frozenQuantity : 0;
        shareholderAccount = shareholderAccount != null ? shareholderAccount : "";
    }
}
// AI_GENERATE_END-----
