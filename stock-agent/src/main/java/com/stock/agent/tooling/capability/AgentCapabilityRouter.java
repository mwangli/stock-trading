// AI_GENERATE_START---------------
package com.stock.agent.tooling.capability;

import com.stock.agent.interaction.dto.AgentTaskStrategy;
import com.stock.agent.interaction.dto.KnowledgeMode;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.Set;

/**
 * Agent 通用对话能力路由器。
 * 为对话统一绑定只读工具目录，由模型按工具语义选择能力，权限和审计仍由 Java 边界负责。
 *
 * @author mwangli
 * @since 2026-10-22
 */
@Component
public class AgentCapabilityRouter {

    /**
     * 为当前问题选择通用对话策略和完整只读工具集。
     *
     * @param knowledgeMode API 指定的知识库模式，为空时按 AUTO 处理
     * @return 能力路由结果
     */
    public AgentCapabilitySelection route(KnowledgeMode knowledgeMode) {
        KnowledgeMode effectiveMode = knowledgeMode == null ? KnowledgeMode.AUTO : knowledgeMode;
        EnumSet<AgentToolName> tools = EnumSet.allOf(AgentToolName.class);
        if (effectiveMode != KnowledgeMode.AUTO) {
            tools.remove(AgentToolName.KNOWLEDGE_SEARCH);
        }
        return new AgentCapabilitySelection(
                AgentTaskStrategy.CONVERSATION, Set.copyOf(tools));
    }
}
// AI_GENERATE_END--------------
