// AI_GENERATE_START ----
package com.stock.agent.service;

import com.stock.agent.model.AdaptiveStrategyFact;
import com.stock.agent.model.BusinessRagSourceDocument;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 从 Backend 自适应策略生命周期中提取稳定业务知识。
 *
 * 只保存策略版本、生命周期和演化记录，不复制账户、持仓、订单或实时行情。
 *
 * @author mwangli
 * @since 2026-10-25
 */
@Component
@RequiredArgsConstructor
public class AdaptiveStrategyBusinessRagSource implements BusinessRagSource {

    private final AgentFactQueryService factQueryService;

    /**
     * 返回策略演化来源名称。
     *
     * @return 来源名称
     */
    @Override
    public String sourceName() {
        return "adaptiveStrategyEvolution";
    }

    /**
     * 查询 Backend 并生成策略演化知识文档。
     *
     * @return 单份共享业务知识文档
     */
    @Override
    public List<BusinessRagSourceDocument> collect() {
        AdaptiveStrategyFact response = factQueryService.adaptiveStrategy();
        StringBuilder content = new StringBuilder("# 自适应策略演化知识\n\n")
                .append("策略版本：").append(value(response.strategyVersion())).append('\n')
                .append("当前模式：").append(value(response.currentMode())).append('\n')
                .append("生命周期阶段：").append(value(response.lifecycleStage())).append('\n')
                .append("生命周期说明：").append(value(response.lifecycleDescription())).append('\n')
                .append("最近演化时间：").append(value(response.lastEvolutionAt())).append('\n');
        appendSection(content, "模型版本摘要", response.versions());
        appendSection(content, "最近演化事件", response.evolutionEvents());
        appendSection(content, "已识别异常", response.anomalies());

        List<String> tags = new ArrayList<>(List.of("业务知识", "策略演化"));
        if (response.strategyVersion() != null && !response.strategyVersion().isBlank()) {
            tags.add("策略版本:" + response.strategyVersion());
        }
        return List.of(new BusinessRagSourceDocument(
                "BUSINESS_KNOWLEDGE",
                "adaptive-strategy-evolution",
                content.toString(),
                List.of(),
                List.copyOf(tags),
                "SHARED",
                "system",
                response.asOf()));
    }

    private void appendSection(StringBuilder content, String title, List<String> items) {
        content.append("\n## ").append(title).append('\n');
        if (items == null || items.isEmpty()) {
            content.append("- 暂无记录\n");
            return;
        }
        items.forEach(item -> content.append("- ").append(value(item)).append('\n'));
    }

    private String value(Object value) {
        return value == null ? "未知" : String.valueOf(value);
    }
}
// AI_GENERATE_END ----
