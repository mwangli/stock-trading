// AI_GENERATE_START --
package com.stock.tradingExecutor.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
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
@Accessors(chain = true)
@Table(name = "broker_order")
public class BrokerOrderEntity {

    /** 数据库主键。 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 本地交易指令编号；纯券商同步记录允许为空。 */
    @Column(name = "command_id", length = 64)
    private String commandId;

    /** 同一交易指令的提交尝试序号。 */
    @Column(name = "attempt_no", nullable = false)
    private Integer attemptNo;

    /** 券商编码。 */
    @Column(name = "broker_code", nullable = false, length = 32)
    private String brokerCode;

    /** 账户哈希标识。 */
    @Column(name = "account_id", nullable = false, length = 64)
    private String accountId;

    /** 股票代码。 */
    @Column(name = "stock_code", length = 16)
    private String stockCode;

    /** 股票名称。 */
    @Column(name = "stock_name", length = 64)
    private String stockName;

    /** 市场或交易类别。 */
    @Column(name = "market", length = 16)
    private String market;

    /** 买卖方向。 */
    @Column(name = "side", length = 16)
    private String side;

    /** 委托所属交易日。 */
    @Column(name = "trade_date")
    private LocalDate tradeDate;

    /** 券商委托编号。 */
    @Column(name = "broker_order_no", length = 64)
    private String brokerOrderNo;

    /** 本地客户端委托编号。 */
    @Column(name = "client_order_no", length = 64)
    private String clientOrderNo;

    /** 标准委托状态。 */
    @Column(name = "status", nullable = false, length = 32)
    private String status;

    /** 券商原始状态。 */
    @Column(name = "raw_status", length = 64)
    private String rawStatus;

    /** 委托价格，单位为元。 */
    @Column(name = "order_price", nullable = false, precision = 18, scale = 4)
    private BigDecimal orderPrice;

    /** 委托数量。 */
    @Column(name = "order_quantity", nullable = false)
    private Integer orderQuantity;

    /** 累计成交数量。 */
    @Column(name = "filled_quantity", nullable = false)
    private Integer filledQuantity;

    /** 成交均价，单位为元。 */
    @Column(name = "average_fill_price", precision = 18, scale = 4)
    private BigDecimal averageFillPrice;

    /** 委托提交时间。 */
    @Column(name = "submitted_at")
    private LocalDateTime submittedAt;

    /** 最近查询时间。 */
    @Column(name = "last_queried_at")
    private LocalDateTime lastQueriedAt;

    /** 进入事实终态的时间。 */
    @Column(name = "terminal_at")
    private LocalDateTime terminalAt;

    /** 券商错误码。 */
    @Column(name = "error_code", length = 64)
    private String errorCode;

    /** 脱敏错误摘要。 */
    @Column(name = "error_message", length = 500)
    private String errorMessage;

    /** 乐观锁版本。 */
    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    /** 记录创建时间。 */
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    /** 记录更新时间。 */
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /**
     * 初始化默认值和创建时间。
     */
    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        attemptNo = attemptNo != null ? attemptNo : 1;
        filledQuantity = filledQuantity != null ? filledQuantity : 0;
        version = version != null ? version : 0L;
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
// AI_GENERATE_END --
