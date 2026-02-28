package com.stock.databus.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "stock_info")
public class StockInfo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 20, nullable = false)
    private String code;

    @Column(length = 100)
    private String name;

    @Column(length = 10)
    private String market;

    @Column
    private Integer isSt;

    @Column
    private Integer isTradable;

    @Column(precision = 18, scale = 4)
    private BigDecimal price;

    @Column(precision = 18, scale = 4)
    private BigDecimal increase;

    @Column(name = "`delete`", length = 1)
    private String deleted;

    @Column
    private LocalDateTime createTime;

    @Column
    private LocalDateTime updateTime;

    // 扩展字段
    @Column(length = 50)
    private String area;

    @Column(length = 50)
    private String industry;

    @Column(length = 8)
    private String listDate;
}
