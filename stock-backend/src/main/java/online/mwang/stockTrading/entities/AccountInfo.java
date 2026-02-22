package online.mwang.stockTrading.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 账户资金实体
 * 对应表: account_info
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "account_info")
public class AccountInfo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "total_asset")
    private BigDecimal totalAsset;

    @Column(name = "available_cash")
    private BigDecimal availableCash;

    @Column(name = "market_value")
    private BigDecimal marketValue;

    @Column(name = "frozen_amount")
    private BigDecimal frozenAmount;

    @Column(name = "daily_pnl")
    private BigDecimal dailyPnl;

    @Column(name = "daily_pnl_ratio")
    private BigDecimal dailyPnlRatio;

    @Column(name = "monthly_pnl")
    private BigDecimal monthlyPnl;

    @Column(name = "monthly_pnl_ratio")
    private BigDecimal monthlyPnlRatio;

    @Column(name = "total_pnl")
    private BigDecimal totalPnl;

    @Column(name = "total_pnl_ratio")
    private BigDecimal totalPnlRatio;

    @Column(name = "position_count")
    private Integer positionCount;

    @Column(name = "update_time")
    private LocalDateTime updateTime;

    @Column(name = "create_time")
    private LocalDateTime createTime;
}
