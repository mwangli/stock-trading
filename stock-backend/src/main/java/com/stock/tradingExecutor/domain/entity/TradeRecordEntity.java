// AI_GENERATE_START -
package com.stock.tradingExecutor.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 已成交委托按 FIFO 买入批次汇总形成的交易记录。
 * 一条记录对应一笔买入委托，可累计匹配一笔或多笔后续卖出委托。
 *
 * @author mwangli
 * @since 2026-10-11
 */
@Data
@Entity
@Accessors(chain = true)
@Table(name = "trade_record")
public class TradeRecordEntity {

    /** 数据库主键。 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 券商编码。 */
    @Column(name = "broker_code", nullable = false, length = 32)
    private String brokerCode;

    /** 账户哈希标识。 */
    @Column(name = "account_id", nullable = false, length = 64)
    private String accountId;

    /** 股票代码。 */
    @Column(name = "stock_code", nullable = false, length = 16)
    private String stockCode;

    /** 股票名称。 */
    @Column(name = "stock_name", length = 64)
    private String stockName;

    /** 市场或交易类别。 */
    @Column(name = "market", length = 16)
    private String market;

    /** 买入委托编号。 */
    @Column(name = "buy_order_no", nullable = false, length = 64)
    private String buyOrderNo;

    /** 已匹配卖出委托编号，按时间顺序使用逗号分隔。 */
    @Column(name = "sell_order_nos", columnDefinition = "TEXT")
    private String sellOrderNos;

    /** 交易状态：OPEN、PARTIAL 或 CLOSED。 */
    @Column(nullable = false, length = 16)
    private String status;

    /** 买入成交时间。 */
    @Column(name = "buy_time", nullable = false)
    private LocalDateTime buyTime;

    /** 首次卖出成交时间。 */
    @Column(name = "first_sell_time")
    private LocalDateTime firstSellTime;

    /** 最后一次卖出成交时间。 */
    @Column(name = "last_sell_time")
    private LocalDateTime lastSellTime;

    /** 买入成交数量。 */
    @Column(name = "buy_quantity", nullable = false)
    private Integer buyQuantity;

    /** 已卖出数量。 */
    @Column(name = "sold_quantity", nullable = false)
    private Integer soldQuantity;

    /** 尚未卖出数量。 */
    @Column(name = "remaining_quantity", nullable = false)
    private Integer remainingQuantity;

    /** 买入成交均价，单位为元。 */
    @Column(name = "buy_price", nullable = false, precision = 18, scale = 4)
    private BigDecimal buyPrice;

    /** 已卖出部分的加权成交均价，单位为元。 */
    @Column(name = "average_sell_price", precision = 18, scale = 4)
    private BigDecimal averageSellPrice;

    /** 买入总金额，不含费用。 */
    @Column(name = "buy_amount", nullable = false, precision = 20, scale = 4)
    private BigDecimal buyAmount;

    /** 已卖出数量对应的买入成本，不含费用。 */
    @Column(name = "sold_cost_amount", nullable = false, precision = 20, scale = 4)
    private BigDecimal soldCostAmount;

    /** 累计卖出金额，不含费用。 */
    @Column(name = "sell_amount", nullable = false, precision = 20, scale = 4)
    private BigDecimal sellAmount;

    /** 整笔买入委托的估算佣金。 */
    @Column(name = "buy_fee", nullable = false, precision = 18, scale = 4)
    private BigDecimal buyFee;

    /** 已卖出数量分摊的买入估算佣金。 */
    @Column(name = "allocated_buy_fee", nullable = false, precision = 18, scale = 4)
    private BigDecimal allocatedBuyFee;

    /** 已匹配卖出委托分摊的估算佣金。 */
    @Column(name = "sell_fee", nullable = false, precision = 18, scale = 4)
    private BigDecimal sellFee;

    /** 毛收益金额，不扣除费用。 */
    @Column(name = "gross_profit", nullable = false, precision = 20, scale = 4)
    private BigDecimal grossProfit;

    /** 净收益金额，扣除已分摊的买卖佣金。 */
    @Column(name = "net_profit", nullable = false, precision = 20, scale = 4)
    private BigDecimal netProfit;

    /** 毛收益率，小数形式，0.1 表示 10%。 */
    @Column(name = "gross_return_rate", nullable = false, precision = 18, scale = 8)
    private BigDecimal grossReturnRate;

    /** 净收益率，小数形式，0.1 表示 10%。 */
    @Column(name = "return_rate", nullable = false, precision = 18, scale = 8)
    private BigDecimal returnRate;

    /** 按各次卖出数量加权的持仓自然日数。 */
    @Column(name = "holding_days", precision = 12, scale = 4)
    private BigDecimal holdingDays;

    /** 按加权持仓天数折算的复合日净收益率。 */
    @Column(name = "daily_return_rate", nullable = false, precision = 18, scale = 8)
    private BigDecimal dailyReturnRate;

    /** 记录创建时间。 */
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    /** 记录更新时间。 */
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /**
     * 初始化创建和更新时间。
     */
    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = createdAt != null ? createdAt : now;
        updatedAt = now;
    }

    /**
     * 刷新记录更新时间。
     */
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
// AI_GENERATE_END -
