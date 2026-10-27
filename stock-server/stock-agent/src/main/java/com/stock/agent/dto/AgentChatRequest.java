// AI_GENERATE_START--------
package com.stock.agent.dto;

import com.stock.agent.enums.KnowledgeMode;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * Agent 统一聊天请求。
 * 用户身份和工具权限由服务端认证上下文决定，请求不承载用户范围和角色信息。
 *
 * @author mwangli
 * @since 2026-10-22
 */
@Data
@NoArgsConstructor
public class AgentChatRequest {

    /** 会话标识，为空时由服务端创建当前用户会话。 */
    private String sessionId;

    /** 用户问题，不能为空，最大 2000 个字符。 */
    private String question;

    /** 当前股票代码上下文，可为空。 */
    private String stockCode;

    /** 当前候选批次标识，可为空。 */
    private String candidateBatchId;

    /** 当前预测批次标识，可为空。 */
    private String predictionBatchId;

    /** 当前模型版本，可为空。 */
    private String modelVersion;

    /** 当前目标交易日，可为空。 */
    private LocalDate targetTradeDate;

    /** 当前页面标识，仅用于补充业务上下文，不作为权限依据。 */
    private String pageContext;

    /** 知识库使用模式，默认 AUTO。 */
    private KnowledgeMode knowledgeMode = KnowledgeMode.AUTO;
}
// AI_GENERATE_END--------
