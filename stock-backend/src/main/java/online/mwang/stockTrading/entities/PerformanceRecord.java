package online.mwang.stockTrading.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 模型表现记录实体
 * Python服务写入，Java服务仅读取
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "performance_records")
public class PerformanceRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "trade_date", nullable = false)
    private LocalDateTime tradeDate;

    @Column(name = "daily_return")
    private Float dailyReturn;

    @Column(name = "cumulative_return")
    private Float cumulativeReturn;

    @Column(name = "win_count")
    private Integer winCount;

    @Column(name = "loss_count")
    private Integer lossCount;

    @Column(name = "total_trades")
    private Integer totalTrades;

    @Column(name = "max_drawdown")
    private Float maxDrawdown;

    @Column(name = "model_version")
    private String modelVersion;

    @Column(name = "create_time")
    private LocalDateTime createTime;

    @Column(name = "update_time")
    private LocalDateTime updateTime;
}
