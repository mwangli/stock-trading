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
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 待回补同步缺口。
 *
 * @author mwangli
 * @since 2026-10-16
 */
@Data
@Entity
@Accessors(chain = true)
@Table(name = "sync_gap")
public class SyncGapEntity {

    /** 数据库主键。 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 数据集名称。 */
    @Column(nullable = false, length = 64)
    private String dataset;

    /** 业务范围。 */
    @Column(name = "scope_key", nullable = false, length = 128)
    private String scopeKey;

    /** 缺口开始日期。 */
    @Column(name = "window_start", nullable = false)
    private LocalDate windowStart;

    /** 缺口结束日期。 */
    @Column(name = "window_end", nullable = false)
    private LocalDate windowEnd;

    /** OPEN、RESOLVED。 */
    @Column(nullable = false, length = 16)
    private String status;

    /** 已重试次数。 */
    @Column(name = "retry_count", nullable = false)
    private Integer retryCount;

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
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
// AI_GENERATE_END -
