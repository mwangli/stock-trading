package online.mwang.stockTrading.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 模型版本管理实体
 * Python服务写入，Java服务仅读取
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "model_versions")
public class ModelVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "model_type", nullable = false)
    private String modelType;

    @Column(name = "version", nullable = false)
    private String version;

    @Column(name = "file_path", nullable = false)
    private String filePath;

    @Column(name = "train_date", nullable = false)
    private LocalDateTime trainDate;

    @Column(name = "accuracy")
    private Float accuracy;

    @Column(name = "is_active")
    private Integer isActive;

    @Column(name = "train_params", columnDefinition = "TEXT")
    private String trainParams;

    @Column(name = "performance_stats", columnDefinition = "JSON")
    private String performanceStats;

    @Column(name = "create_time")
    private LocalDateTime createTime;
}
