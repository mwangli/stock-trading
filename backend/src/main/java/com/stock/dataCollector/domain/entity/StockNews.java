package com.stock.dataCollector.domain.entity;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;

import java.time.LocalDateTime;

/**
 * 股票新闻实体
 * 存储从证券平台采集的股票相关新闻，用于情感分析等下游任务
 *
 * @author mwangli
 * @since 2026-03-14
 */
@Data
@Document(collection = "stock_news")
@CompoundIndex(name = "idx_stock_publish", def = "{'stockCode': 1, 'publishTime': -1}")
public class StockNews {

    @Id
    private String id;

    /**
     * 证券平台新闻 ID，用于去重，避免重复采集
     * 索引名 externalId 与库中已有索引一致，避免 IndexOptionsConflict
     */
    @Indexed(unique = true, name = "externalId")
    private String externalId;

    /**
     * 新闻标题
     */
    private String title;

    /**
     * 新闻正文内容
     */
    private String content;

    /**
     * 关联股票代码，如 600519
     */
    private String stockCode;

    /**
     * 来源媒体，如 上海证券交易所
     */
    private String source;

    /**
     * 采集来源类型：新闻、公告
     */
    private String category;

    /**
     * 新闻发布时间
     */
    private LocalDateTime publishTime;

    /**
     * 本地入库时间
     */
    private LocalDateTime createTime;
}
