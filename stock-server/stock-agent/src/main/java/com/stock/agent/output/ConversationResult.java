// AI_GENERATE_START ----
package com.stock.agent.output;

import lombok.Data;

import java.util.List;

/**
 * Agent 通用对话结构化结果。
 *
 * 使用完整回答作为摘要字段，兼容现有任务持久化和前端聊天响应契约。
 *
 * @author mwangli
 * @since 2026-10-25
 */
@Data
public class ConversationResult implements AgentStructuredOutput {

    /** 面向用户的完整中文回答。 */
    private String summary;

    /** 回答状态，仅允许 PASS、WARN 或 UNKNOWN。 */
    private String status;

    /** 本轮静态证据编号；动态工具证据由服务端审计补充。 */
    private List<String> evidenceRefs;

    /** 数据时效、工具失败或事实不足说明，可为空。 */
    private String uncertainty;
}
// AI_GENERATE_END ----