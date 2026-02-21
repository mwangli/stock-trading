package online.mwang.stockTrading.entities;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.LocalDateTime;

/**
 * 财经新闻实体
 * 存储在MongoDB news集合中
 * Python服务写入，Java服务仅读取
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "news")
public class StockNews {
    
    @Id
    private String id;
    
    @Field("news_id")
    private String newsId;
    
    @Field("stock_code")
    private String stockCode;
    
    @Field("stock_name")
    private String stockName;
    
    @Field("title")
    private String title;
    
    @Field("content")
    private String content;
    
    @Field("url")
    private String url;
    
    @Field("source")
    private String source;
    
    @Field("publish_time")
    private LocalDateTime publishTime;
    
    @Field("create_time")
    private LocalDateTime createTime;
    
    @Field("sentiment")
    private String sentiment;
    
    @Field("sentiment_score")
    private Double sentimentScore;
}
