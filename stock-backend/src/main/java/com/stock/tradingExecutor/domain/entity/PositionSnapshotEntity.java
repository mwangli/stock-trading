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

import java.time.LocalDateTime;

/**
 * 券商持仓快照头实体。
 *
 * @author mwangli
 * @since 2026-09-25
 */
@Data
@Entity
@Accessors(chain = true)
@Table(name = "position_snapshot")
public class PositionSnapshotEntity {

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

    /** 账户哈希标识。 */
    @Column(name = "account_id", nullable = false, length = 64)
    private String accountId;

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
    }
}
// AI_GENERATE_END --
