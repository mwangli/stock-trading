package online.mwang.stockTrading.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.Date;

/**
 * V3.0 LSTM预测结果实体
 * Python服务写入，Java服务仅读取
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "stock_prediction")
public class StockPrediction {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "stock_code")
    private String stockCode;
    
    @Column(name = "stock_name")
    private String stockName;
    
    @Column(name = "predict_date")
    private LocalDate predictDate;
    
    @Column(name = "predict_price")
    private Double predictPrice;
    
    @Column(name = "predict_direction")
    private String predictDirection;
    
    @Column(name = "confidence")
    private Double confidence;
    
    @Column(name = "model_version")
    private String modelVersion;
    
    @Column(name = "test_deviation")
    private Double testDeviation;
    
    @Column(name = "features", columnDefinition = "JSON")
    private String features;
    
    @Column(name = "create_time")
    @Temporal(TemporalType.TIMESTAMP)
    private Date createTime;
    
    @Column(name = "update_time")
    @Temporal(TemporalType.TIMESTAMP)
    private Date updateTime;
}
