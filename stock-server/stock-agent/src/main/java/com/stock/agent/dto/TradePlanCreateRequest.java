// AI_GENERATE_START------
package com.stock.agent.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 创建待人工审批交易计划的请求。
 *
 * @author mwangli
 * @since 2026-10-23
 */
@Data
public class TradePlanCreateRequest {

    /** 候选批次标识，不能为空。 */
    private String candidateBatchId;

    /** 预测批次标识，买入计划不能为空。 */
    private String predictionBatchId;

    /** 股票代码，不能为空。 */
    private String stockCode;

    /** 交易方向，仅允许 BUY 或 SELL。 */
    private String direction;

    /** 计划金额，单位为元，买入计划必须大于零。 */
    private BigDecimal plannedAmount;

    /** 卖出计划数量，单位为股，买入计划可为空。 */
    private Integer plannedQuantity;

    /** 脱敏证据引用列表，不能为空。 */
    private List<String> evidenceRefs;

    /** 计划有效期，必须晚于创建时间。 */
    private LocalDateTime expiresAt;

    /** 用户范围内的幂等键，不能为空。 */
    private String idempotencyKey;

    /** 目标交易日，可为空。 */
    private LocalDate targetTradeDate;

    /** 策略配置版本，可为空。 */
    private String modelVersion;

    /** 价格约束说明，可为空。 */
    private String priceConstraint;

    /** 计划理由，可为空。 */
    private String reason;

    /** 风险提示列表，可为空。 */
    private List<String> riskWarnings;

    /** 不确定性说明，可为空。 */
    private String uncertainty;
}
// AI_GENERATE_END------
