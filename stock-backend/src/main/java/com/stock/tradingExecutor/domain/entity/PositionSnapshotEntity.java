// AI_GENERATE_START --
package com.stock.tradingExecutor.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
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
@Entity
@Accessors(chain = true)
@Table(name = "position_snapshot", indexes = {
        @jakarta.persistence.Index(name = "idx_position_snapshot_account_capture",
                columnList = "account_id,captured_at"),
        @jakarta.persistence.Index(name = "idx_position_snapshot_id_stock",
                columnList = "snapshot_id,stock_code")
})
public class PositionSnapshotEntity {

    /** 数据库主键。 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 全局快照编号。 */
    @Column(name = "snapshot_id", nullable = false, length = 64)
    private String snapshotId;

    /** 券商编码。 */
    @Column(name = "broker_code", nullable = false, length = 32)
    private String brokerCode;

    /** 账户哈希标识。 */
    @Column(name = "account_id", nullable = false, length = 64)
    private String accountId;

    /** 股票代码；空值表示本次快照没有持仓明细。 */
    @Column(name = "stock_code", length = 16)
    private String stockCode;

    /** 股票名称。 */
    @Column(name = "stock_name", length = 64)
    private String stockName;

    /** 市场或交易类别。 */
    @Column(name = "market", length = 16)
    private String market;

    /** 股东账号哈希标识。 */
    @Column(name = "shareholder_account", nullable = false, length = 64)
    private String shareholderAccount;

    /** 总持仓数量。 */
    @Column(name = "total_quantity", nullable = false)
    private Integer totalQuantity;

    /** 当前可卖数量。 */
    @Column(name = "available_quantity", nullable = false)
    private Integer availableQuantity;

    /** 冻结数量。 */
    @Column(name = "frozen_quantity", nullable = false)
    private Integer frozenQuantity;

    /** 平均成本，单位为元。 */
    @Column(name = "average_cost", precision = 18, scale = 4)
    private BigDecimal averageCost;

    /** 当前价格，单位为元。 */
    @Column(name = "current_price", precision = 18, scale = 4)
    private BigDecimal currentPrice;

    /** 当前市值，单位为元。 */
    @Column(name = "market_value", precision = 20, scale = 4)
    private BigDecimal marketValue;

    /** 首次买入日期。 */
    @Column(name = "first_buy_date")
    private LocalDate firstBuyDate;

    /** 最近买入日期。 */
    @Column(name = "last_buy_date")
    private LocalDate lastBuyDate;

    /** 券商采集时间。 */
    @Column(name = "captured_at", nullable = false)
    private LocalDateTime capturedAt;

    /** 快照来源。 */
    @Column(name = "source", nullable = false, length = 32)
    private String source;

    /** 脱敏原始事件引用。 */
    @Column(name = "raw_event_id", length = 64)
    private String rawEventId;

    /** 记录创建时间。 */
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    /**
     * 初始化创建时间。
     */
    @PrePersist
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
// AI_GENERATE_END --
