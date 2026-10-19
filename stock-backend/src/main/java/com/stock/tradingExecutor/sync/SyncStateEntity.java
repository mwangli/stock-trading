// AI_GENERATE_START -
package com.stock.tradingExecutor.sync;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 数据同步单表状态。
 * 同时保存成功水位和当前待回补缺口，避免拆分成两张互相关联的状态表。
 *
 * @author mwangli
 * @since 2026-10-19
 */
@Data
@Entity
@Accessors(chain = true)
@Table(name = "sync_state", uniqueConstraints = @UniqueConstraint(
        name = "uk_sync_state_scope", columnNames = {"dataset", "scope_key"}))
public class SyncStateEntity {

    /** 数据库主键。 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 数据集名称。 */
    @Column(nullable = false, length = 64)
    private String dataset;

    /** 业务范围，例如股票代码或账户标识。 */
    @Column(name = "scope_key", nullable = false, length = 128)
    private String scopeKey;

    /** 最近确认成功的供应商游标。 */
    @Column(name = "last_success_cursor", length = 256)
    private String lastSuccessCursor;

    /** 最近确认成功的业务日期。 */
    @Column(name = "last_success_date")
    private LocalDate lastSuccessDate;

    /** 缺口开始日期。 */
    @Column(name = "gap_start_date")
    private LocalDate gapStartDate;

    /** 缺口结束日期。 */
    @Column(name = "gap_end_date")
    private LocalDate gapEndDate;

    /** 缺口状态，OPEN 或 RESOLVED。 */
    @Column(name = "gap_status", nullable = false, length = 16)
    private String gapStatus = "RESOLVED";

    /** 缺口重试次数。 */
    @Column(name = "retry_count", nullable = false)
    private Integer retryCount = 0;

    /** 最近一次失败摘要。 */
    @Column(name = "last_error", length = 500)
    private String lastError;

    /** 创建时间。 */
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    /** 更新时间。 */
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = createdAt == null ? now : createdAt;
        updatedAt = now;
        retryCount = retryCount == null ? 0 : retryCount;
        gapStatus = gapStatus == null ? "RESOLVED" : gapStatus;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
// AI_GENERATE_END -
