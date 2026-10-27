// AI_GENERATE_START-----
package com.stock.agent.dto;

import com.stock.agent.output.AgentStructuredOutput;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Agent 生成的结构化交易计划草案。
 * 该对象不包含券商订单号、会话凭据和直接执行指令。
 *
 * @author mwangli
 * @since 2026-10-17
 */
@Data
public class TradePlanDraft implements AgentStructuredOutput {

    /** 计划摘要。 */
    private String summary;

    /** 草案状态，固定为 PENDING_APPROVAL。 */
    private String status;

    /** 候选批次标识。 */
    private String candidateBatchId;

    /** 预测批次标识。 */
    private String predictionBatchId;

    /** 股票代码。 */
    private String stockCode;

    /** 交易方向。 */
    private String direction;

    /** 计划金额。 */
    private BigDecimal plannedAmount;

    /** 计划数量。 */
    private Integer plannedQuantity;

    /** 目标交易日。 */
    private LocalDate targetTradeDate;

    /** 策略配置版本。 */
    private String strategyVersion;

    /** 计划有效期。 */
    private LocalDateTime expiresAt;

    /** 价格约束说明。 */
    private String priceConstraint;

    /** 生成理由。 */
    private String reason;

    /** 风险提示。 */
    private List<String> riskWarnings;

    /** 证据引用。 */
    private List<String> evidenceRefs;

    /** 不确定性说明。 */
    private String uncertainty;

    /** 幂等键。 */
    private String idempotencyKey;
}
// AI_GENERATE_END----