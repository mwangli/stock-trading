// AI_GENERATE_START --
package com.stock.strategyAgent.decision;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
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
@Entity
@Table(name = "trade_decision_context", indexes = {
        @Index(name = "idx_trade_decision_context_batch", columnList = "candidate_batch_id,created_at"),
        @Index(name = "idx_trade_decision_context_expire", columnList = "expires_at")
})
public class TradeDecisionContextEntity {

    /** 上下文唯一标识。 */
    @Id
    @Column(name = "context_id", length = 64)
    private String contextId;

    /** 上下文规范化内容的 SHA-256。 */
    @Column(name = "context_hash", nullable = false, unique = true, length = 64)
    private String contextHash;

    /** 候选批次标识。 */
    @Column(name = "candidate_batch_id", nullable = false, length = 128)
    private String candidateBatchId;

    /** 股票代码。 */
    @Column(name = "stock_code", nullable = false, length = 32)
    private String stockCode;

    /** 模型输入基准交易日。 */
    @Column(name = "base_trade_date")
    private LocalDate baseTradeDate;

    /** 候选目标交易日。 */
    @Column(name = "target_trade_date")
    private LocalDate targetTradeDate;

    /** 候选是否通过确定性基线。 */
    @Column(name = "baseline_eligible")
    private Boolean baselineEligible;

    /** 决策基准收盘价，单位为元。 */
    @Column(name = "baseline_close_price", precision = 20, scale = 6)
    private BigDecimal baselineClosePrice;

    /** 完整上下文 JSON 快照。 */
    @Lob
    @Column(name = "context_json", nullable = false, columnDefinition = "LONGTEXT")
    private String contextJson;

    /** 信息截止时间。 */
    @Column(name = "information_cutoff_at", nullable = false)
    private LocalDateTime informationCutoffAt;

    /** 上下文失效时间。 */
    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    /** 上下文创建时间。 */
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
// AI_GENERATE_END --
