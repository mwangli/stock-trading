package com.stock.databus.entity;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Data
@Document(collection = "stock_news")
public class StockNews {

    @Id
    private String id;

    private String title;

    private String content;

    private String source;

    private String stockCode;

    private String pubTime;

    private String url;

    private LocalDateTime createTime;
}
