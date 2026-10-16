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
 * 同步成功水位。
 *
 * @author mwangli
 * @since 2026-10-16
 */
@Data
@Entity
@Accessors(chain = true)
@Table(name = "sync_checkpoint", uniqueConstraints = @UniqueConstraint(
        name = "uk_sync_checkpoint_scope", columnNames = {"dataset", "scope_key"}))
public class SyncCheckpointEntity {

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

    /** 最近确认成功的业务日期。 */
    @Column(name = "last_success_date")
    private LocalDate lastSuccessDate;

    /** 最近确认成功的供应商游标。 */
    @Column(name = "last_success_cursor", length = 256)
    private String lastSuccessCursor;

    /** 更新时间。 */
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    @PreUpdate
    protected void touch() {
        updatedAt = LocalDateTime.now();
    }
}
// AI_GENERATE_END -
