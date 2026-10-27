// AI_GENERATE_START---------
package com.stock.trading.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;

import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 券商委托事实实体。
 * 可保存由本地交易指令产生的委托，也可保存券商历史同步得到的外部委托。
 *
 * @author mwangli
 * @since 2026-09-25
 */
@Data
@Entity
@Table(name = "broker_order", uniqueConstraints = {
        @UniqueConstraint(name = "uk_broker_order_fact",
                columnNames = {"broker_code", "account_id", "trade_date", "broker_order_no"})
})
@Accessors(chain = true)
public class BrokerOrderEntity {

    /** 数据库主键。 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 本地交易指令编号；纯券商同步记录允许为空。 */
    private String commandId;

    /** 同一交易指令的提交尝试序号。 */
    private Integer attemptNo;

    /** 券商编码。 */
    private String brokerCode;

    /** 账户哈希标识。 */
    private String accountId;

    /** 股票代码。 */
    private String stockCode;

    /** 股票名称。 */
    private String stockName;

    /** 市场或交易类别。 */
    private String market;

    /** 买卖方向。 */
    private String side;

    /** 委托所属交易日。 */
    private LocalDate tradeDate;

    /** 券商委托编号。 */
    private String brokerOrderNo;

    /** 本地客户端委托编号。 */
    private String clientOrderNo;

    /** 标准委托状态。 */
    private String status;

    /** 券商原始状态。 */
    private String rawStatus;

    /** 委托价格，单位为元。 */
    @Column(precision = 38, scale = 12)
    private BigDecimal orderPrice;

    /** 委托数量。 */
    private Integer orderQuantity;

    /** 累计成交数量。 */
    private Integer filledQuantity;

    /** 成交均价，单位为元。 */
    @Column(precision = 38, scale = 12)
    private BigDecimal averageFillPrice;

    /** 委托提交时间。 */
    private LocalDateTime submittedAt;

    /** 最近查询时间。 */
    private LocalDateTime lastQueriedAt;

    /** 进入事实终态的时间。 */
    private LocalDateTime terminalAt;

    /** 券商错误码。 */
    private String errorCode;

    /** 脱敏错误摘要。 */
    @Lob
    @Column(columnDefinition = "LONGTEXT")
    private String errorMessage;

    /** 乐观锁版本。 */
    @Version
    private Long version;

    /** 记录创建时间。 */
    private LocalDateTime createdAt;

    /** 记录更新时间。 */
    private LocalDateTime updatedAt;

    /**
     * 初始化默认值和创建时间。
     */
    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        attemptNo = attemptNo != null ? attemptNo : 1;
        filledQuantity = filledQuantity != null ? filledQuantity : 0;
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
// AI_GENERATE_END---------
