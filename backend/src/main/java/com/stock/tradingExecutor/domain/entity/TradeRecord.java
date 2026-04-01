package com.stock.tradingExecutor.domain.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.experimental.Accessors;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 交易记录实体
 * 由历史订单组装而成的完整交易记录，包含买入和对应的卖出订单组合
 *
 * @author mwangli
 * @since 2026-04-01
 */
@Data
@Entity
@Table(name = "trade_record", indexes = {
    @Index(name = "idx_trade_id", columnList = "tradeId", unique = true),
    @Index(name = "idx_stock_code", columnList = "stockCode"),
    @Index(name = "idx_trade_date", columnList = "tradeDate")
})
@Accessors(chain = true)
@EntityListeners(AuditingEntityListener.class)
public class TradeRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 交易唯一标识
     * 格式: 买入订单号_卖出订单号1_卖出订单号2...
     */
    @Column(nullable = false, length = 256, unique = true)
    private String tradeId;

    /**
     * 股票代码
     */
    @Column(nullable = false, length = 16)
    private String stockCode;

    /**
     * 股票名称
     */
    @Column(nullable = false, length = 64)
    private String stockName;

    /**
     * 买入订单号
     */
    @Column(nullable = false, length = 32)
    private String buyOrderNo;

    /**
     * 卖出订单号列表（逗号分隔）
     */
    @Column(length = 512)
    private String sellOrderNos;

    /**
     * 交易日期（买入日期）
     */
    @Column(length = 8)
    private String tradeDate;

    /**
     * 买入时间
     */
    private LocalDateTime buyTime;

    /**
     * 最后卖出时间
     */
    private LocalDateTime lastSellTime;

    /**
     * 持仓时间（秒）
     */
    private Long holdingSeconds;

    /**
     * 买入总金额
     */
    @Column(precision = 16, scale = 4)
    private BigDecimal buyAmount;

    /**
     * 卖出总金额
     */
    @Column(precision = 16, scale = 4)
    private BigDecimal sellAmount;

    /**
     * 收益金额（卖出总金额 - 买入总金额）
     */
    @Column(precision = 16, scale = 4)
    private BigDecimal profitAmount;

    /**
     * 买入手续费
     */
    @Column(precision = 12, scale = 4)
    private BigDecimal buyFee;

    /**
     * 卖出手续费
     */
    @Column(precision = 12, scale = 4)
    private BigDecimal sellFee;

    /**
     * 其他费用
     */
    @Column(precision = 12, scale = 4)
    private BigDecimal otherFee;

    /**
     * 总手续费
     */
    @Column(precision = 12, scale = 4)
    private BigDecimal totalFee;

    /**
     * 净收益金额（收益金额 - 总手续费）
     */
    @Column(precision = 16, scale = 4)
    private BigDecimal netProfitAmount;

    /**
     * 总收益率（净收益金额 / 买入总金额 × 100%）
     */
    @Column(precision = 10, scale = 4)
    private BigDecimal totalReturnRate;

    /**
     * 日收益率（总收益率 / 持仓天数）
     */
    @Column(precision = 10, scale = 4)
    private BigDecimal dailyReturnRate;

    /**
     * 交易状态：
     * COMPLETED - 已完成（所有卖出已成交）
     * PARTIAL - 部分完成（部分卖出已成交）
     * PENDING - 待完成（尚未卖出）
     */
    @Column(length = 16)
    private String status;

    /**
     * 备注
     */
    @Column(length = 256)
    private String remark;

    @CreatedDate
    @Column(updatable = false)
    private LocalDateTime createTime;

    @LastModifiedDate
    private LocalDateTime updateTime;
}