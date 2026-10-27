// AI_GENERATE_START----------
package com.stock.agent.dto;

import com.stock.agent.enums.KnowledgeMode;

import java.time.LocalDate;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Research 任务请求。
 *
 * @author mwangli
 * @since 2026-10-18
 */
@Data
@NoArgsConstructor
public class ResearchRequest {
    /** 会话标识，可为空。 */
    private String sessionId;
    /** 用户问题。 */
    private String question;
    /** 当前候选批次上下文，可为空。 */
    private String candidateBatchId;
    /** 当前股票上下文，可为空。 */
    private String stockCode;
    /** 当前策略版本上下文，可为空。 */
    private String strategyVersion;
    /** 当前预测批次上下文，可为空。 */
    private String predictionBatchId;
    /** 当前目标交易日上下文，可为空。 */
    private LocalDate targetTradeDate;
    /** 知识库使用模式，默认 AUTO。 */
    private KnowledgeMode knowledgeMode = KnowledgeMode.AUTO;
}
// AI_GENERATE_END---------
