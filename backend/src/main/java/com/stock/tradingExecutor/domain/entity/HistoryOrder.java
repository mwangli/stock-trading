package com.stock.tradingExecutor.domain.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.experimental.Accessors;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 历史订单实体
 * 存储从券商API同步的历史委托/成交记录
 *
 * @author mwangli
 * @since 2026-03-31
 */
@Data
@Entity
@Table(name = "history_order", indexes = {
    @Index(name = "idx_order_no", columnList = "orderNo", unique = true),
    @Index(name = "idx_stock_code", columnList = "stockCode"),
    @Index(name = "idx_order_date", columnList = "orderDate")
})
@Accessors(chain = true)
@EntityListeners(AuditingEntityListener.class)
public class HistoryOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 委托日期，格式 yyyyMMdd
     */
    @Column(nullable = false, length = 8)
    private String orderDate;

    /**
     * 委托编号（唯一标识）
     */
    @Column(nullable = false, length = 32, unique = true)
    private String orderNo;

    /**
     * 市场类型
     */
    @Column(length = 16)
    private String marketType;

    /**
     * 股东帐号
     */
    @Column(length = 32)
    private String stockAccount;

    /**
     * 股票代码
     */
    @Column(length = 16)
    private String stockCode;

    /**
     * 股票名称
     */
    @Column(length = 64)
    private String stockName;

    /**
     * 买卖方向：B=买入，S=卖出
     */
    @Column(length = 4)
    private String direction;

    /**
     * 委托/成交价格
     */
    @Column(precision = 12, scale = 4)
    private BigDecimal price;

    /**
     * 委托/成交数量
     */
    private Integer quantity;

    /**
     * 成交金额
     */
    @Column(precision = 16, scale = 4)
    private BigDecimal amount;

    /**
     * 流水号
     */
    @Column(length = 32)
    private String serialNo;

    /**
     * 委托时间
     */
    @Column(length = 8)
    private String orderTime;

    /**
     * 订单原始提交时间（委托日期+委托时间，用于展示和排序）
     */
    private LocalDateTime orderSubmitTime;

    /**
     * 备注
     */
    @Column(length = 128)
    private String remark;

    /**
     * 证券全称
     */
    @Column(length = 128)
    private String fullName;

    /**
     * 同步批次号，用于断点续传
     */
    @Column(length = 64)
    private String syncBatchNo;

    /**
     * 最后同步时间
     */
    private LocalDateTime lastSyncTime;

    @CreatedDate
    @Column(updatable = false)
    private LocalDateTime createTime;

    @LastModifiedDate
    private LocalDateTime updateTime;

    /**
     * 判断是否为买入
     */
    public boolean isBuy() {
        return "B".equalsIgnoreCase(direction) || "买入".equals(direction);
    }

    /**
     * 判断是否为卖出
     */
    public boolean isSell() {
        return "S".equalsIgnoreCase(direction) || "卖出".equals(direction);
    }
}
