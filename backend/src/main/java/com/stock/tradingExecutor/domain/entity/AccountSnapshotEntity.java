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
import java.time.LocalDateTime;

/**
 * 券商账户资金快照实体。
 *
 * @author mwangli
 * @since 2026-09-25
 */
@Data
@Entity
@Accessors(chain = true)
@Table(name = "account_snapshot")
public class AccountSnapshotEntity {

    /** 数据库主键。 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 全局快照编号。 */
    @Column(name = "snapshot_id", nullable = false, unique = true, length = 64)
    private String snapshotId;

    /** 券商编码。 */
    @Column(name = "broker_code", nullable = false, length = 32)
    private String brokerCode;

    /** 账户哈希标识，不保存资金账号明文。 */
    @Column(name = "account_id", nullable = false, length = 64)
    private String accountId;

    /** 总资产，单位为元。 */
    @Column(name = "total_assets", precision = 20, scale = 4)
    private BigDecimal totalAssets;

    /** 可用资金，单位为元。 */
    @Column(name = "available_cash", precision = 20, scale = 4)
    private BigDecimal availableCash;

    /** 冻结资金，单位为元。 */
    @Column(name = "frozen_cash", precision = 20, scale = 4)
    private BigDecimal frozenCash;

    /** 持仓市值，单位为元。 */
    @Column(name = "market_value", precision = 20, scale = 4)
    private BigDecimal marketValue;

    /** 当日盈亏，单位为元；券商未提供时为空。 */
    @Column(name = "daily_profit_loss", precision = 20, scale = 4)
    private BigDecimal dailyProfitLoss;

    /** 券商采集时间。 */
    @Column(name = "captured_at", nullable = false)
    private LocalDateTime capturedAt;

    /** 快照来源，如 CURRENT_SYNC。 */
    @Column(name = "source", nullable = false, length = 32)
    private String source;

    /** 脱敏原始事件引用；未落原始事件时为空。 */
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
    }
}
// AI_GENERATE_END --
