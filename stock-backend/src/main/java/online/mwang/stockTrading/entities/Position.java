package online.mwang.stockTrading.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 持仓实体
 * 对应表: positions
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "positions")
public class Position {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "stock_code", nullable = false)
    private String stockCode;

    @Column(name = "stock_name")
    private String stockName;

    @Column(name = "quantity")
    private Integer quantity;

    @Column(name = "available_quantity")
    private Integer availableQuantity;

    @Column(name = "frozen_quantity")
    private Integer frozenQuantity;

    @Column(name = "avg_cost")
    private BigDecimal avgCost;

    @Column(name = "current_price")
    private BigDecimal currentPrice;

    @Column(name = "market_value")
    private BigDecimal marketValue;

    @Column(name = "unrealized_pnl")
    private BigDecimal unrealizedPnl;

    @Column(name = "unrealized_pnl_ratio")
    private BigDecimal unrealizedPnlRatio;

    @Column(name = "open_date")
    private LocalDate openDate;

    @Column(name = "position_side")
    private String positionSide; // LONG/SHORT

    @Column(name = "status")
    private String status; // HOLDING/SOLD/STOP_LOSS

    @Column(name = "create_time")
    private LocalDateTime createTime;

    @Column(name = "update_time")
    private LocalDateTime updateTime;

    /**
     * 计算未实现盈亏
     */
    public void calculateUnrealizedPnl() {
        if (currentPrice != null && avgCost != null && quantity != null) {
            this.unrealizedPnl = currentPrice.subtract(avgCost).multiply(new BigDecimal(quantity));
            if (avgCost.compareTo(BigDecimal.ZERO) > 0) {
                this.unrealizedPnlRatio = currentPrice.subtract(avgCost).divide(avgCost, 4, BigDecimal.ROUND_HALF_UP);
            }
            this.marketValue = currentPrice.multiply(new BigDecimal(quantity));
        }
    }
}
