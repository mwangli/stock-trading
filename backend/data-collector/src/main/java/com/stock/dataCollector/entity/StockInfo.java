package com.stock.dataCollector.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 股票基本信息实体
 * 用于存储股票基础数据到MySQL
 */
@Data
@Entity
@Table(name = "stock_info", indexes = {
    @Index(name = "idx_stock_code", columnList = "code", unique = true),
    @Index(name = "idx_stock_name", columnList = "name"),
    @Index(name = "idx_market", columnList = "market")
})
public class StockInfo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 股票代码 (如: 000001)
     */
    @Column(name = "code", length = 10, nullable = false, unique = true)
    private String code;

    /**
     * 股票名称
     */
    @Column(name = "name", length = 50)
    private String name;

    /**
     * 市场代码 (SH/SZ/BJ)
     */
    @Column(name = "market", length = 10)
    private String market;

    /**
     * 当前价格
     */
    @Column(name = "price", precision = 10, scale = 3)
    private BigDecimal price;

    /**
     * 涨跌额
     */
    @Column(name = "change_amount", precision = 10, scale = 3)
    private BigDecimal changeAmount;

    /**
     * 涨跌幅
     */
    @Column(name = "change_percent", precision = 10, scale = 4)
    private BigDecimal changePercent;

    /**
     * 总市值
     */
    @Column(name = "total_market_value", precision = 20, scale = 2)
    private BigDecimal totalMarketValue;

    /**
     * 换手率
     */
    @Column(name = "turnover_rate", precision = 10, scale = 4)
    private BigDecimal turnoverRate;

    /**
     * 创建时间
     */
    @Column(name = "create_time", updatable = false)
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    @Column(name = "update_time")
    private LocalDateTime updateTime;

    /**
     * 在持久化前设置创建时间
     */
    @PrePersist
    protected void onCreate() {
        createTime = LocalDateTime.now();
        updateTime = LocalDateTime.now();
    }

    /**
     * 在更新前设置更新时间
     */
    @PreUpdate
    protected void onUpdate() {
        updateTime = LocalDateTime.now();
    }
}