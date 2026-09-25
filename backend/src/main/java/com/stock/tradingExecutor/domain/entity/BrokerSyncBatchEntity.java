// AI_GENERATE_START --
package com.stock.tradingExecutor.domain.entity;

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
 * 券商数据同步批次实体。
 *
 * @author mwangli
 * @since 2026-09-25
 */
@Data
@Entity
@Accessors(chain = true)
@Table(name = "broker_sync_batch")
public class BrokerSyncBatchEntity {

    /** 数据库主键。 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 全局同步批次编号。 */
    @Column(name = "batch_id", nullable = false, unique = true, length = 64)
    private String batchId;

    /** 券商编码。 */
    @Column(name = "broker_code", nullable = false, length = 32)
    private String brokerCode;

    /** 账户哈希标识。 */
    @Column(name = "account_id", nullable = false, length = 64)
    private String accountId;

    /** 同步类型。 */
    @Column(name = "sync_type", nullable = false, length = 32)
    private String syncType;

    /** 同步窗口开始日期。 */
    @Column(name = "window_start")
    private LocalDate windowStart;

    /** 同步窗口结束日期。 */
    @Column(name = "window_end")
    private LocalDate windowEnd;

    /** 分页或续传游标。 */
    @Column(name = "cursor_value", length = 128)
    private String cursorValue;

    /** CREATED、RUNNING、SUCCESS、PARTIAL_SUCCESS 或 FAILED。 */
    @Column(name = "status", nullable = false, length = 32)
    private String status;

    /** 请求次数。 */
    @Column(name = "request_count", nullable = false)
    private Integer requestCount;

    /** 接收记录数。 */
    @Column(name = "received_count", nullable = false)
    private Integer receivedCount;

    /** 新增记录数。 */
    @Column(name = "inserted_count", nullable = false)
    private Integer insertedCount;

    /** 更新记录数。 */
    @Column(name = "updated_count", nullable = false)
    private Integer updatedCount;

    /** 跳过记录数。 */
    @Column(name = "skipped_count", nullable = false)
    private Integer skippedCount;

    /** 脱敏后的最近错误摘要。 */
    @Column(name = "last_error", length = 500)
    private String lastError;

    /** 同步开始时间。 */
    @Column(name = "started_at")
    private LocalDateTime startedAt;

    /** 同步结束时间。 */
    @Column(name = "finished_at")
    private LocalDateTime finishedAt;

    /** 记录创建时间。 */
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    /** 记录更新时间。 */
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /**
     * 初始化计数和时间字段。
     */
    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        requestCount = requestCount != null ? requestCount : 0;
        receivedCount = receivedCount != null ? receivedCount : 0;
        insertedCount = insertedCount != null ? insertedCount : 0;
        updatedCount = updatedCount != null ? updatedCount : 0;
        skippedCount = skippedCount != null ? skippedCount : 0;
        createdAt = createdAt != null ? createdAt : now;
        updatedAt = now;
    }

    /**
     * 刷新记录更新时间。
     */
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
// AI_GENERATE_END --
