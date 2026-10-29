// AI_GENERATE_START ---
package com.stock.model.entity;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 新闻特征来源日期完备性水位。
 * 日期达到 COMPLETE 后，缺少稀疏聚合记录的股票日才能确定性解释为 NO_EVENT。
 *
 * @author mwangli
 * @since 2026-10-28
 */
@Data
@Document(collection = "news_feature_completeness")
@CompoundIndex(name = "uk_news_feature_completeness",
        def = "{'featureVersion': 1, 'marketDataSource': 1, 'marketDataSourceVersion': 1, 'featureTradeDate': 1}", unique = true)
public class NewsFeatureCompletenessDocument {

    /** `特征版本|行情来源|行情版本|交易日` 组成的 MongoDB 文档 ID。 */
    @Id
    private String id;

    /** 新闻特征契约版本。 */
    private String featureVersion;

    /** 模型行情数据来源。 */
    private String marketDataSource;

    /** 模型行情数据版本。 */
    private String marketDataSourceVersion;

    /** 特征归属交易日。 */
    private LocalDate featureTradeDate;

    /** 完备状态：COMPLETE 或 FAILED。 */
    private String status;

    /** 新闻采集、清洗和事件事实管线版本。 */
    private String pipelineVersion;

    /** 原始文档数量；历史阶段无法可靠恢复时为空。 */
    private Long rawDocumentCount;

    /** 去重后的有效事件数量。 */
    private long deduplicatedEventCount;

    /** 进入稀疏股票日聚合的股票数量。 */
    private long eventStockCount;

    /** 被内容质量或角色规则过滤的数量；无法可靠恢复时为空。 */
    private Long filteredCount;

    /** 特征准备失败数量，COMPLETE 时必须为零。 */
    private long failedCount;

    /** 当前日期生成的稀疏股票日快照数量。 */
    private long generatedSnapshotCount;

    /** 当前日期复用的已有稀疏股票日快照数量。 */
    private long existingSnapshotCount;

    /** 进入当前日期特征计算的最晚事件发布时间，可为空。 */
    private LocalDateTime latestInputPublishTime;

    /** 当前日期完成全部准备和校验的时间。 */
    private LocalDateTime completedAt;

    /** 最近失败原因，不包含原始正文和敏感信息。 */
    private String lastError;
}
// AI_GENERATE_END ---
