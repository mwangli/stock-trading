// AI_GENERATE_START-----
package com.stock.agent.decision.dto;

import com.stock.agent.runtime.AgentStructuredOutput;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Agent 针对单个不可变上下文生成的结构化交易判断。
 * 该对象只承载交易建议，不包含真实委托参数和执行权限。
 *
 * @author mwangli
 * @since 2026-10-23
 */
@Data
public class AgentTradeDecisionDraft implements AgentStructuredOutput {

    /** 判断摘要，不能为空。 */
    private String summary;

    /** 判断状态，仅允许 VALID 或 ABSTAINED。 */
    private String status;

    /** 关联上下文标识。 */
    private String contextId;

    /** 关联上下文 SHA-256。 */
    private String contextHash;

    /** 候选批次标识。 */
    private String candidateBatchId;

    /** 股票代码。 */
    private String stockCode;

    /** 结构化建议，仅允许 BUY、HOLD、SKIP 或 ABSTAIN。 */
    private String recommendation;

    /** 受控原因码集合。 */
    private List<String> reasonCodes;

    /** 核心判断说明，不能作为执行参数。 */
    private String thesis;

    /** 支持判断的上下文证据编号。 */
    private List<String> supportingEvidenceRefs;

    /** 与判断相反或冲突的上下文证据编号。 */
    private List<String> contraryEvidenceRefs;

    /** 数据、事件、波动和模型风险标记。 */
    private List<String> riskFlags;

    /** 判断失效条件，仅用于审计和后续评估。 */
    private List<String> invalidationConditions;

    /** 相对优先级，范围 0 至 100。 */
    private Integer suggestedPriority;

    /** 建议仓位下限，小数形式，范围 0 至 1。 */
    private Double allocationMin;

    /** 建议仓位上限，小数形式，范围 0 至 1。 */
    private Double allocationMax;

    /** 模型自评置信区间，仅允许 LOW、MEDIUM 或 HIGH。 */
    private String confidenceBand;

    /** 判断有效期，不得晚于上下文有效期。 */
    private LocalDateTime validUntil;

    /** 本次输出引用的全部上下文证据编号。 */
    private List<String> evidenceRefs;

    /** 不确定性和证据缺口说明。 */
    private String uncertainty;
}
// AI_GENERATE_END-----
