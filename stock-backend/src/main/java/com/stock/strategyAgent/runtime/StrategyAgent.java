// AI_GENERATE_START ---
package com.stock.strategyAgent.runtime;

import dev.langchain4j.service.Result;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

/**
 * LangChain4j 高层 Agent 接口。
 *
 * @author mwangli
 * @since 2026-10-18
 */
public interface StrategyAgent {

    /**
     * 根据系统约束和用户任务生成结果。
     *
     * @param systemPrompt 系统约束
     * @param userPrompt 用户任务
     * @return 包含模型文本和Token统计的调用结果
     */
    @SystemMessage("{{systemPrompt}}")
    Result<String> chat(@UserMessage String userPrompt, @V("systemPrompt") String systemPrompt);
}
// AI_GENERATE_END ---
