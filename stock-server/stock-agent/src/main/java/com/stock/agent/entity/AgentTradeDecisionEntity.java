// AI_GENERATE_START-------
package com.stock.agent.entity;

import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.index.CompoundIndex;

import org.springframework.data.annotation.Id;

import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/**
 * Agent 单候选结构化交易建议。
 *
 * @author mwangli
 * @since 2026-10-23
 */
@Data
@Accessors(chain = true)
@Document(collection = "agent_trade_suggestion")
@CompoundIndex(name = "uk_agent_trade_suggestion_context_prompt",
        def = "{'contextHash': 1, 'promptVersion': 1}", unique = true)
public class AgentTradeDecisionEntity {

    /** 决策唯一标识。 */
    @Id
    private String decisionId;

    /** 关联上下文标识。 */
    private String contextId;

    /** 关联上下文哈希。 */
    private String contextHash;

    /** 候选批次标识。 */
    private String candidateBatchId;

    /** 股票代码。 */
    private String stockCode;

    /** 判断绑定的策略配置版本。 */
    private String strategyVersion;

    /** 判断绑定的目标交易日。 */
    private java.time.LocalDate targetTradeDate;

    /** Agent 建议。 */
    private String recommendation;

    /** 受控原因码 JSON。 */
    private String reasonCodes;

    /** 判断摘要。 */
    private String summary;

    /** 核心判断说明。 */
    private String thesis;

    /** 支持证据引用 JSON。 */
    private String supportingEvidenceRefs;

    /** 反证引用 JSON。 */
    private String contraryEvidenceRefs;

    /** 风险标记 JSON。 */
    private String riskFlags;

    /** 判断失效条件 JSON。 */
    private String invalidationConditions;

    /** 建议优先级，范围 0 至 100。 */
    private Integer suggestedPriority;

    /** 建议仓位下限，小数形式。 */
    private Double allocationMin;

    /** 建议仓位上限，小数形式。 */
    private Double allocationMax;

    /** 模型自评置信区间。 */
    private String confidenceBand;

    /** 决策状态。 */
    private String status;

    /** Provider 名称。 */
    private String provider;

    /** 模型名称。 */
    private String model;

    /** 提示词契约版本。 */
    private String promptVersion;

    /** 不确定性说明。 */
    private String uncertainty;

    /** 决策有效期。 */
    private LocalDateTime validUntil;

    /** 决策创建时间。 */
    private LocalDateTime createdAt;
}
// AI_GENERATE_END-------