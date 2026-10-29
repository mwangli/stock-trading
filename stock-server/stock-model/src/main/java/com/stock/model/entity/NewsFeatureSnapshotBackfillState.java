// AI_GENERATE_START --
package com.stock.model.entity;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 新闻日级特征稀疏准备日期游标和累计状态。
 *
 * @author mwangli
 * @since 2026-10-25
 */
@Data
@Document(collection = "news_feature_snapshot_backfill_states")
public class NewsFeatureSnapshotBackfillState {

    /** 特征、行情来源和行情版本组成的状态标识。 */
    @Id
    private String id;

    /** 最后成功完成的特征交易日。 */
    private LocalDate lastProcessedDate;

    /** 累计完成准备的来源日期数量。 */
    private long processedDateCount;

    /** 累计生成的有事件稀疏快照数量。 */
    private long generatedSnapshotCount;

    /** 累计复用的已有稀疏快照数量。 */
    private long existingSnapshotCount;

    /** 累计失败日期数量。 */
    private long failedDateCount;

    /** 任务状态：PENDING、RUNNING、COMPLETED 或 FAILED。 */
    private String status;

    /** 最近失败原因，不包含原始文本和连接凭据。 */
    private String lastError;

    /** 状态最近更新时间。 */
    private LocalDateTime updatedAt;
}
// AI_GENERATE_END --
