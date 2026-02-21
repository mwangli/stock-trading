package online.mwang.stockTrading.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.Date;

/**
 * V3.0 股票综合评分排名实体
 * Python服务写入，Java服务仅读取
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "stock_ranking")
public class StockRanking {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "stock_code")
    private String stockCode;
    
    @Column(name = "stock_name")
    private String stockName;
    
    @Column(name = "composite_score")
    private Double compositeScore;
    
    @Column(name = "sentiment_score")
    private Double sentimentScore;
    
    @Column(name = "momentum_score")
    private Double momentumScore;
    
    @Column(name = "valuation_score")
    private Double valuationScore;
    
    @Column(name = "technical_score")
    private Double technicalScore;
    
    @Column(name = "rank_date")
    private LocalDate rankDate;
    
    @Column(name = "rank_position")
    private Integer rankPosition;
    
    @Column(name = "create_time")
    @Temporal(TemporalType.TIMESTAMP)
    private Date createTime;
    
    @Column(name = "update_time")
    @Temporal(TemporalType.TIMESTAMP)
    private Date updateTime;
}
