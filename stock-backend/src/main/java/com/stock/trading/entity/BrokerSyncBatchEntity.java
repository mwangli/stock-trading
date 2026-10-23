// AI_GENERATE_START-----
package com.stock.trading.entity;

import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;

import org.springframework.data.annotation.Id;

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
@Document(collection = "broker_sync_batch")
@CompoundIndex(name = "uk_broker_sync_window",
        def = "{'brokerCode': 1, 'accountId': 1, 'syncType': 1, 'windowStart': 1, 'windowEnd': 1}", unique = true)
@Accessors(chain = true)
public class BrokerSyncBatchEntity {

    /** 数据库主键。 */
    @Id
    private Long id;

    /** 全局同步批次编号。 */
    @Indexed(unique = true, name = "uk_broker_sync_batch_id")
    private String batchId;

    /** 券商编码。 */
    private String brokerCode;

    /** 账户哈希标识。 */
    private String accountId;

    /** 同步类型。 */
    private String syncType;

    /** 同步窗口开始日期。 */
    private LocalDate windowStart;

    /** 同步窗口结束日期。 */
    private LocalDate windowEnd;

    /** 分页或续传游标。 */
    private String cursorValue;

    /** CREATED、RUNNING、SUCCESS、PARTIAL_SUCCESS 或 FAILED。 */
    private String status;

    /** 请求次数。 */
    private Integer requestCount;

    /** 接收记录数。 */
    private Integer receivedCount;

    /** 新增记录数。 */
    private Integer insertedCount;

    /** 更新记录数。 */
    private Integer updatedCount;

    /** 跳过记录数。 */
    private Integer skippedCount;

    /** 脱敏后的最近错误摘要。 */
    private String lastError;

    /** 同步开始时间。 */
    private LocalDateTime startedAt;

    /** 同步结束时间。 */
    private LocalDateTime finishedAt;

    /** 记录创建时间。 */
    private LocalDateTime createdAt;

    /** 记录更新时间。 */
    private LocalDateTime updatedAt;

    /**
     * 初始化计数和时间字段。
     */
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
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
// AI_GENERATE_END-----
