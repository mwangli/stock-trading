// AI_GENERATE_START-----
package com.stock.trading.entity;

import org.springframework.data.annotation.Version;

import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.index.CompoundIndex;

import org.springframework.data.annotation.Id;

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
@Document(collection = "broker_order")
@CompoundIndex(name = "uk_broker_order_fact",
        def = "{'brokerCode': 1, 'accountId': 1, 'tradeDate': 1, 'brokerOrderNo': 1}", unique = true)
@Accessors(chain = true)
public class BrokerOrderEntity {

    /** 数据库主键。 */
    @Id
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
    private BigDecimal orderPrice;

    /** 委托数量。 */
    private Integer orderQuantity;

    /** 累计成交数量。 */
    private Integer filledQuantity;

    /** 成交均价，单位为元。 */
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
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
// AI_GENERATE_END-----
