// AI_GENERATE_START-----------
package com.stock.agent.entity;

import org.springframework.data.annotation.Version;

import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.index.CompoundIndex;

import org.springframework.data.annotation.Id;

import com.stock.agent.domain.AgentEnums;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalDate;

/**
 * Agent 生成并等待人工审批的交易计划。
 * 计划只描述候选和执行约束，批准后仍需由 stock-trading 重新读取当前事实。
 *
 * @author mwangli
 * @since 2026-10-17
 */
@Data
@Accessors(chain = true)
@Document(collection = "agent_trade_plan")
@CompoundIndex(name = "uk_agent_trade_plan_user_key",
        def = "{'userScope': 1, 'idempotencyKey': 1}", unique = true)
public class AgentTradePlanEntity {

    /** 交易计划唯一标识。 */
    @Id
    private String planId;

    /** 计划所属认证用户范围。 */
    private String userScope;

    /** 候选批次标识。 */
    private String candidateBatchId;

    /** 预测批次标识。 */
    private String predictionBatchId;

    /** 计划涉及的股票代码。 */
    private String stockCode;

    /** 交易方向，仅允许 BUY 或 SELL。 */
    private String direction;

    /** 计划金额，实际价格和数量由 stock-trading 重新计算。 */
    private BigDecimal plannedAmount;

    /** 卖出计划数量，买入计划为空。 */
    private Integer plannedQuantity;

    /** 证据引用，保存脱敏后的 ID 或版本摘要。 */
    private String evidenceRefs;

    /** 计划有效期。 */
    private LocalDateTime expiresAt;

    /** 防重复执行幂等键。 */
    private String idempotencyKey;

    /** 自动交易计划状态。 */
    private AgentEnums.TradePlanStatus status;

    /** 创建时间。 */
    private LocalDateTime createdAt;

    /** 更新时间。 */
    private LocalDateTime updatedAt;

    /** 乐观锁版本号，防止多个执行节点重复抢占同一计划。 */
    @Version
    private Long version;

    /** 最近一次拒绝或执行摘要。 */
    private String decisionMessage;

    /** 目标交易日。 */
    private LocalDate targetTradeDate;

    /** 策略配置版本。 */
    private String strategyVersion;

    /** 价格约束说明。 */
    private String priceConstraint;

    /** 计划理由。 */
    private String reason;

    /** 风险提示 JSON。 */
    private String riskWarnings;

    /** 不确定性说明。 */
    private String uncertainty;

    /** 批准人用户范围。 */
    private String approvedBy;

    /** 批准时间。 */
    private LocalDateTime approvedAt;

    /** 拒绝人用户范围。 */
    private String rejectedBy;

    /** 拒绝时间。 */
    private LocalDateTime rejectedAt;

    /** 人工审批意见。 */
    private String reviewComment;

}
// AI_GENERATE_END-----------
