package com.stock.dataCollector.domain.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 股票基本信息实体，用于存储股票基础数据到 MySQL
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

    @Column(name = "code", length = 10, nullable = false, unique = true)
    private String code;

    @Column(name = "name", length = 50)
    private String name;

    @Column(name = "market", length = 10)
    private String market;

    @Column(name = "price", precision = 10, scale = 3)
    private BigDecimal price;

    @Column(name = "change_amount", precision = 10, scale = 3)
    private BigDecimal changeAmount;

    @Column(name = "change_percent", precision = 10, scale = 4)
    private BigDecimal changePercent;

    @Column(name = "total_market_value", precision = 20, scale = 2)
    private BigDecimal totalMarketValue;

    @Column(name = "turnover_rate", precision = 10, scale = 4)
    private BigDecimal turnoverRate;

    @Column(name = "volume_ratio", precision = 10, scale = 4)
    private BigDecimal volumeRatio;

    @Column(name = "industry_code")
    private Integer industryCode;

    @Column(name = "create_time", updatable = false)
    private LocalDateTime createTime;

    @Column(name = "update_time")
    private LocalDateTime updateTime;

    @PrePersist
    protected void onCreate() {
        createTime = LocalDateTime.now();
        updateTime = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updateTime = LocalDateTime.now();
    }
}
