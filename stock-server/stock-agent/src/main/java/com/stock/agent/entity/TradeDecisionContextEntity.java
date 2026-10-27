// AI_GENERATE_START------
package com.stock.agent.entity;

import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.index.Indexed;

import org.springframework.data.annotation.Id;

import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 不可变交易决策上下文持久化实体。
 *
 * @author mwangli
 * @since 2026-10-23
 */
@Data
@Accessors(chain = true)
@Document(collection = "trade_decision_context")
public class TradeDecisionContextEntity {

    /** 上下文唯一标识。 */
    @Id
    private String contextId;

    /** 上下文规范化内容的 SHA-256。 */
    @Indexed(unique = true, name = "uk_trade_decision_context_hash")
    private String contextHash;

    /** 候选批次标识。 */
    private String candidateBatchId;

    /** 股票代码。 */
    private String stockCode;

    /** 模型输入基准交易日。 */
    private LocalDate baseTradeDate;

    /** 候选目标交易日。 */
    private LocalDate targetTradeDate;

    /** 候选是否通过确定性基线。 */
    private Boolean baselineEligible;

    /** 决策基准收盘价，单位为元。 */
    private BigDecimal baselineClosePrice;

    /** 完整上下文 JSON 快照。 */
    private String contextJson;

    /** 信息截止时间。 */
    private LocalDateTime informationCutoffAt;

    /** 上下文失效时间。 */
    private LocalDateTime expiresAt;

    /** 上下文创建时间。 */
    private LocalDateTime createdAt;
}
// AI_GENERATE_END------