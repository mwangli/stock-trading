package com.stock.dataCollector.entity;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

/**
 * 股票新闻实体
 * TODO: 待实现新闻采集功能
 */
@Data
@Document(collection = "stock_news")
public class StockNews {

    @Id
    private String id;

    /**
     * 新闻标题
     */
    private String title;

    /**
     * 新闻内容
     */
    private String content;

    /**
     * 相关股票代码
     */
    private String stockCode;

    /**
     * 新闻来源
     */
    private String source;

    /**
     * 发布时间
     */
    private LocalDateTime publishTime;

    /**
     * 采集时间
     */
    private LocalDateTime createTime;
}