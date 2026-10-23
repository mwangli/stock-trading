// AI_GENERATE_START -
package com.stock.data.entity;

import com.stock.data.enums.NewsBackfillStatus;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

/**
 * 新闻采集治理历史回填游标和累计统计。
 *
 * @author mwangli
 * @since 2026-10-23
 */
@Data
@Document(collection = "news_collection_backfill_states")
public class NewsCollectionBackfillState {

    /** 管线版本，同时作为 MongoDB 文档 ID。 */
    @Id
    private String pipelineVersion;

    /** 最后成功提交批次的 MongoDB 文档 ID。 */
    private String lastProcessedId;

    /** 累计扫描文档数。 */
    private long scannedCount;

    /** 累计更新原始文档数。 */
    private long updatedCount;

    /** 累计同步事件事实数。 */
    private long eventFactCount;

    /** 累计忽略文档数。 */
    private long ignoredCount;

    /** 累计失败文档数。 */
    private long failedCount;

    /** 回填运行状态。 */
    private NewsBackfillStatus status;

    /** 最近失败原因，不包含敏感连接信息。 */
    private String lastError;

    /** 状态最近更新时间。 */
    private LocalDateTime updateTime;
}
// AI_GENERATE_END -
