// AI_GENERATE_START----
package com.stock.agent.dto;

import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 单个候选的 Agent 交易建议响应。
 *
 * @author mwangli
 * @since 2026-10-23
 */
@Value
@Builder
public class TradeDecisionResponse {

    /** 判断唯一标识。 */
    String decisionId;

    /** 不可变上下文标识。 */
    String contextId;

    /** 不可变上下文 SHA-256。 */
    String contextHash;

    /** 候选批次标识。 */
    String candidateBatchId;

    /** 股票代码。 */
    String stockCode;

    /** Agent 结构化建议。 */
    String recommendation;

    /** 判断状态。 */
    String status;

    /** 判断摘要。 */
    String summary;

    /** 受控原因码。 */
    List<String> reasonCodes;

    /** 核心判断说明。 */
    String thesis;

    /** 支持判断的证据编号。 */
    List<String> supportingEvidenceRefs;

    /** 反证和冲突证据编号。 */
    List<String> contraryEvidenceRefs;

    /** 风险标记。 */
    List<String> riskFlags;

    /** 判断失效条件。 */
    List<String> invalidationConditions;

    /** 相对优先级，范围 0 至 100。 */
    Integer suggestedPriority;

    /** 建议仓位下限，小数形式。 */
    Double allocationMin;

    /** 建议仓位上限，小数形式。 */
    Double allocationMax;

    /** 模型自评置信区间。 */
    String confidenceBand;

    /** 模型 Provider。 */
    String provider;

    /** 模型名称。 */
    String model;

    /** 提示词契约版本。 */
    String promptVersion;

    /** 不确定性说明。 */
    String uncertainty;

    /** 判断有效期。 */
    LocalDateTime validUntil;

    /** 判断创建时间。 */
    LocalDateTime createdAt;
}
// AI_GENERATE_END----
