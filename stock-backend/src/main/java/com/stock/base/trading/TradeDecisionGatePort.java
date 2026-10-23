// AI_GENERATE_START--
package com.stock.base.trading;

import java.time.LocalDateTime;

/**
 * 自动交易候选的可选决策门禁契约。
 *
 * <p>交易模块只依赖该稳定契约，不感知 Agent 的模型、持久化和运行实现。</p>
 *
 * @author mwangli
 * @since 2026-10-23
 */
public interface TradeDecisionGatePort {

    /**
     * 判断 Java 基线候选是否允许继续进入交易复核链。
     *
     * @param candidateBatchId 候选批次标识
     * @param stockCode 股票代码
     * @param strategyVersion 策略版本
     * @param baselineEligible Java 基线是否允许
     * @param evaluatedAt 评估时间
     * @return 门禁结果
     */
    TradeDecisionGateResult evaluate(String candidateBatchId,
                                     String stockCode,
                                     String strategyVersion,
                                     boolean baselineEligible,
                                     LocalDateTime evaluatedAt);
}
// AI_GENERATE_END--
