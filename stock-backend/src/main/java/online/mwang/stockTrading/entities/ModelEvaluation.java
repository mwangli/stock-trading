package online.mwang.stockTrading.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 模型评估结果实体
 * Python服务写入，Java服务仅读取
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "model_evaluation_results")
public class ModelEvaluation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "model_type", nullable = false)
    private String modelType;

    @Column(name = "eval_date", nullable = false)
    private LocalDateTime evalDate;

    @Column(name = "period_days")
    private Integer periodDays;

    @Column(name = "total_return")
    private Float totalReturn;

    @Column(name = "win_rate")
    private Float winRate;

    @Column(name = "max_drawdown")
    private Float maxDrawdown;

    @Column(name = "consecutive_loss_days")
    private Integer consecutiveLossDays;

    @Column(name = "score")
    private Integer score;

    @Column(name = "need_retrain")
    private Integer needRetrain;

    @Column(name = "reason")
    private String reason;

    @Column(name = "create_time")
    private LocalDateTime createTime;
}
