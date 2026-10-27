// AI_GENERATE_START-------
package com.stock.agent.dto;

import lombok.Builder;
import lombok.Value;

/**
 * 单个 Provider 与模型组合的交易建议稳定性指标。
 *
 * @author mwangli
 * @since 2026-10-23
 */
@Value
@Builder
public class TradeDecisionProviderMetricResponse {

    /** Provider 名称，无法识别时为 UNKNOWN。 */
    String provider;

    /** 模型名称，无法识别时为 UNKNOWN。 */
    String model;

    /** 交易建议总数。 */
    long totalCount;

    /** 结构化有效判断数量。 */
    long validCount;

    /** 显式弃权数量。 */
    long abstainedCount;

    /** Provider 超时数量。 */
    long timeoutCount;

    /** 结构化输出或上下文校验失败数量。 */
    long invalidOutputCount;

    /** Provider 未形成可用结果数量。 */
    long unavailableCount;

    /** 支持证据为空的判断数量。 */
    long evidenceGapCount;

    /** 结构化有效率，范围 0 至 1。 */
    double structuredValidRate;

    /** 显式弃权率，范围 0 至 1。 */
    double abstainRate;

}
// AI_GENERATE_END------