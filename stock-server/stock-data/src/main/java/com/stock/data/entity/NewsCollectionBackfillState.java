// AI_GENERATE_START-----
package com.stock.data.entity;

import com.stock.data.enums.NewsBackfillStatus;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

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

    /** 累计识别出的重复文档数。 */
    private long duplicateCount;

    /** 累计忽略文档数。 */
    private long ignoredCount;

    /** 按真实文档类型统计的累计数量。 */
    private Map<String, Long> documentTypeCounts = new LinkedHashMap<>();

    /** 按业务角色统计的累计数量。 */
    private Map<String, Long> documentRoleCounts = new LinkedHashMap<>();

    /** 累计失败文档数。 */
    private long failedCount;

    /** 尚未隔离的失败文档重试次数，键为 MongoDB 文档 ID。 */
    private Map<String, Integer> failureRetryCounts = new LinkedHashMap<>();

    /** 回填运行状态。 */
    private NewsBackfillStatus status;

    /** 最近失败原因，不包含敏感连接信息。 */
    private String lastError;

    /** 状态最近更新时间。 */
    private LocalDateTime updateTime;
}
// AI_GENERATE_END-----
