// AI_GENERATE_START ----
package com.stock.strategyAgent.api;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * TradePlanDraft 生成请求。
 *
 * @author mwangli
 * @since 2026-10-18
 */
@Data
@NoArgsConstructor
public class TradePlanDraftRequest {
    /** 会话标识，可为空。 */
    private String sessionId;
    /** 用户问题。 */
    private String question;
    /** 候选批次标识。 */
    private String candidateBatchId;
    /** 股票代码，可为空。 */
    private String stockCode;
}
// AI_GENERATE_END ----
