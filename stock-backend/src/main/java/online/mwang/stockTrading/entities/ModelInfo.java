package online.mwang.stockTrading.entities;

import jakarta.persistence.*;
import lombok.Data;

import java.util.Date;

/**
 * 模型信息实体
 */
@Data
@Entity
@Table(name = "model_info")
public class ModelInfo {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "code")
    private String code;

    @Column(name = "name")
    private String name;

    @Column(name = "train_period")
    private String trainPeriod;

    @Column(name = "train_times")
    private Integer trainTimes;

    @Column(name = "test_deviation")
    private Double testDeviation;

    @Column(name = "score")
    private Double score;

    @Column(name = "create_time")
    @Temporal(TemporalType.TIMESTAMP)
    private Date createTime;

    @Column(name = "update_time")
    @Temporal(TemporalType.TIMESTAMP)
    private Date updateTime;

    @Column(name = "status")
    private String status;
}
