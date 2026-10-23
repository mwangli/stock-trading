// AI_GENERATE_START------
package com.stock.trading.sync.entity;

import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.index.CompoundIndex;

import org.springframework.data.annotation.Id;

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
@Document(collection = "sync_state")
@CompoundIndex(name = "uk_sync_state_scope", def = "{'dataset': 1, 'scopeKey': 1}", unique = true)
@Accessors(chain = true)
public class SyncStateEntity {

    /** 数据库主键。 */
    @Id
    private Long id;

    /** 数据集名称。 */
    private String dataset;

    /** 业务范围，例如股票代码或账户标识。 */
    private String scopeKey;

    /** 最近确认成功的供应商游标。 */
    private String lastSuccessCursor;

    /** 最近确认成功的业务日期。 */
    private LocalDate lastSuccessDate;

    /** 缺口开始日期。 */
    private LocalDate gapStartDate;

    /** 缺口结束日期。 */
    private LocalDate gapEndDate;

    /** 缺口状态，OPEN 或 RESOLVED。 */
    private String gapStatus = "RESOLVED";

    /** 缺口重试次数。 */
    private Integer retryCount = 0;

    /** 最近一次失败摘要。 */
    private String lastError;

    /** 创建时间。 */
    private LocalDateTime createdAt;

    /** 更新时间。 */
    private LocalDateTime updatedAt;
    /** 初始化同步状态的创建与更新时间。 */
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = createdAt == null ? now : createdAt;
        updatedAt = now;
        retryCount = retryCount == null ? 0 : retryCount;
        gapStatus = gapStatus == null ? "RESOLVED" : gapStatus;
    }
    /** 刷新同步状态的更新时间。 */
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
// AI_GENERATE_END------
