// AI_GENERATE_START --
package com.stock.tradingExecutor.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 券商成交事实实体。
 *
 * @author mwangli
 * @since 2026-09-25
 */
@Data
@Entity
@Accessors(chain = true)
@Table(name = "broker_fill")
public class BrokerFillEntity {

    /** 数据库主键。 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 关联的本地券商委托主键。 */
    @Column(name = "broker_order_id")
    private Long brokerOrderId;

    /** 券商编码。 */
    @Column(name = "broker_code", nullable = false, length = 32)
    private String brokerCode;

    /** 账户哈希标识。 */
    @Column(name = "account_id", nullable = false, length = 64)
    private String accountId;

    /** 券商成交编号或稳定合成键。 */
    @Column(name = "broker_fill_no", nullable = false, length = 64)
    private String brokerFillNo;

    /** 券商委托编号。 */
    @Column(name = "broker_order_no", length = 64)
    private String brokerOrderNo;

    /** 是否使用本地稳定哈希生成成交键。 */
    @Column(name = "synthetic_key", nullable = false)
    private Boolean syntheticKey;

    /** 股票代码。 */
    @Column(name = "stock_code", nullable = false, length = 16)
    private String stockCode;

    /** 股票名称。 */
    @Column(name = "stock_name", length = 64)
    private String stockName;

    /** 市场或交易类别。 */
    @Column(name = "market", length = 16)
    private String market;

    /** 买卖方向。 */
    @Column(name = "side", nullable = false, length = 16)
    private String side;

    /** 成交价格，单位为元。 */
    @Column(name = "fill_price", nullable = false, precision = 18, scale = 4)
    private BigDecimal fillPrice;

    /** 成交数量。 */
    @Column(name = "fill_quantity", nullable = false)
    private Integer fillQuantity;

    /** 成交金额，单位为元。 */
    @Column(name = "fill_amount", nullable = false, precision = 20, scale = 4)
    private BigDecimal fillAmount;

    /** 佣金，单位为元。 */
    @Column(name = "commission", precision = 18, scale = 4)
    private BigDecimal commission;

    /** 印花税，单位为元。 */
    @Column(name = "stamp_duty", precision = 18, scale = 4)
    private BigDecimal stampDuty;

    /** 过户费，单位为元。 */
    @Column(name = "transfer_fee", precision = 18, scale = 4)
    private BigDecimal transferFee;

    /** 其他费用，单位为元。 */
    @Column(name = "other_fee", precision = 18, scale = 4)
    private BigDecimal otherFee;

    /** 成交日期。 */
    @Column(name = "trade_date", nullable = false)
    private LocalDate tradeDate;

    /** 成交时间。 */
    @Column(name = "filled_at", nullable = false)
    private LocalDateTime filledAt;

    /** 脱敏原始事件引用。 */
    @Column(name = "raw_event_id", length = 64)
    private String rawEventId;

    /** 记录创建时间。 */
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    /**
     * 初始化创建时间和合成键标志。
     */
    @PrePersist
    protected void onCreate() {
        createdAt = createdAt != null ? createdAt : LocalDateTime.now();
        syntheticKey = syntheticKey != null ? syntheticKey : Boolean.FALSE;
    }
}
// AI_GENERATE_END --
