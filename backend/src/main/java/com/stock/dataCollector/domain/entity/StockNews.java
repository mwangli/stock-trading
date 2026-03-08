package com.stock.dataCollector.domain.entity;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.index.CompoundIndex;

import java.time.LocalDateTime;

/**
 * 股票新闻实体
 */
@Data
@Document(collection = "stock_news")
@CompoundIndex(name = "idx_stock_publish", def = "{'stockCode': 1, 'publishTime': -1}")
public class StockNews {

    @Id
    private String id;
    private String title;
    private String content;
    private String stockCode;
    private String source;
    private LocalDateTime publishTime;
    private LocalDateTime createTime;
}
