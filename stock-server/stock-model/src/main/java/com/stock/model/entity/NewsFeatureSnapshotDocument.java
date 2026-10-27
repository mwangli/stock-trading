// AI_GENERATE_START --
package com.stock.model.entity;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 股票日级新闻时序特征快照。
 * 快照在固定信息截止时间生成，训练和推理只读取版本化向量，不在线重复执行情感模型。
 *
 * @author mwangli
 * @since 2026-10-24
 */
@Data
@Document(collection = "news_feature_snapshots")
@CompoundIndex(name = "uk_news_feature_snapshot",
        def = "{'stockCode': 1, 'featureTradeDate': 1, 'featureVersion': 1}", unique = true)
public class NewsFeatureSnapshotDocument {

    /** MongoDB 文档 ID。 */
    @Id
    private String id;

    /** 股票代码。 */
    private String stockCode;

    /** 特征归属交易日。 */
    private LocalDate featureTradeDate;

    /** 信息截止时间，任何输入事件不得晚于该时间。 */
    private LocalDateTime cutoffTime;

    /** 新闻特征契约版本。 */
    private String featureVersion;

    /** 固定 25 维连续特征向量。 */
    private List<Double> features;

    /** 情感模型版本。 */
    private String sentimentModelVersion;

    /** 新闻采集治理版本。 */
    private String pipelineVersion;

    /** 模型行情数据来源。 */
    private String marketDataSource;

    /** 模型行情数据版本。 */
    private String marketDataSourceVersion;

    /** 进入快照计算的规范事件数量。 */
    private int inputEventCount;

    /** 进入快照计算的最晚事件发布时间，可为空。 */
    private LocalDateTime latestInputPublishTime;

    /** 快照是否通过维度、时间和来源完整性检查。 */
    private boolean complete;

    /** 快照生成时间。 */
    private LocalDateTime generatedAt;
}
// AI_GENERATE_END --
