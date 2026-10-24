// AI_GENERATE_START----
package com.stock.agent.runtime.provider.entity;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Agent API 每日费用预算持久化记录。
 *
 * @author mwangli
 * @since 2026-10-22
 */
@Data
@Document(collection = "agent_api_daily_budgets")
@CompoundIndex(name = "uk_agent_api_budget_provider_date", def = "{'provider': 1, 'usageDate': 1}", unique = true)
public class AgentApiDailyBudgetDocument {

    /** Provider 与日期组成的唯一标识，格式为 provider:yyyy-MM-dd。 */
    @Id
    private String id;

    /** 模型 Provider 名称。 */
    private String provider;

    /** 费用归属日期，使用北京时间。 */
    private LocalDate usageDate;

    /** 已占用预算，单位人民币微元，包含已结算费用和未释放预留。 */
    private long allocatedCostMicros;

    /** 已按 Token 用量结算的估算费用，单位人民币微元。 */
    private long settledCostMicros;

    /** 已结算输入 Token 数量。 */
    private long inputTokens;

    /** 已结算输出 Token 数量。 */
    private long outputTokens;

    /** 已结算模型请求次数。 */
    private long requestCount;

    /** 调用失败或Token统计缺失的保守结算次数。 */
    private long conservativeSettlementCount;

    /** 记录创建时间。 */
    private LocalDateTime createdAt;

    /** 记录更新时间。 */
    private LocalDateTime updatedAt;
}
// AI_GENERATE_END---
