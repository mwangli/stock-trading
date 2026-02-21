package online.mwang.stockTrading.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.Date;

/**
 * V3.0 情感分析结果实体
 * Python服务写入，Java服务仅读取
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "stock_sentiment")
public class StockSentiment {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "stock_code")
    private String stockCode;
    
    @Column(name = "stock_name")
    private String stockName;
    
    @Column(name = "sentiment_score")
    private Double sentimentScore;
    
    @Column(name = "positive_ratio")
    private Double positiveRatio;
    
    @Column(name = "negative_ratio")
    private Double negativeRatio;
    
    @Column(name = "neutral_ratio")
    private Double neutralRatio;
    
    @Column(name = "news_count")
    private Integer newsCount;
    
    @Column(name = "source")
    private String source;
    
    @Column(name = "analyze_date")
    private LocalDate analyzeDate;
    
    @Column(name = "analyze_time")
    @Temporal(TemporalType.TIMESTAMP)
    private Date analyzeTime;
    
    @Column(name = "create_time")
    @Temporal(TemporalType.TIMESTAMP)
    private Date createTime;
    
    @Column(name = "update_time")
    @Temporal(TemporalType.TIMESTAMP)
    private Date updateTime;
}
