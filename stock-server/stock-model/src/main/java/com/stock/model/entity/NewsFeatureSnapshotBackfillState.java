// AI_GENERATE_START -
package com.stock.model.entity;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

/**
 * 新闻日级特征快照回填游标和累计状态。
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

    /** 最后成功推进的模型日线文档 ID。 */
    private String lastProcessedBarId;

    /** 累计扫描模型日线数量。 */
    private long scannedCount;

    /** 累计生成快照数量。 */
    private long generatedCount;

    /** 累计跳过已存在快照数量。 */
    private long skippedCount;

    /** 累计无效模型日线数量。 */
    private long failedCount;

    /** 任务状态：PENDING、RUNNING、COMPLETED 或 FAILED。 */
    private String status;

    /** 最近失败原因，不包含原始文本和连接凭据。 */
    private String lastError;

    /** 状态最近更新时间。 */
    private LocalDateTime updatedAt;
}
// AI_GENERATE_END -
