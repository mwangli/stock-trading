// AI_GENERATE_START ----
package com.stock.agent.tool;

import com.stock.agent.dto.RagResult;
import com.stock.agent.service.AgentKnowledgeContextService;
import com.stock.agent.model.ToolExecutionContext;
import com.stock.agent.dto.KnowledgeSearchItem;
import com.stock.agent.dto.KnowledgeSearchRequest;
import com.stock.agent.dto.KnowledgeSearchResult;
import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 当前认证用户范围内的业务 RAG 检索工具。
 *
 * 用户范围只从服务端工具上下文读取，模型不能传入或覆盖；每轮最多调用一次。
 *
 * @author mwangli
 * @since 2026-10-25
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class KnowledgeSearchTool {

    private static final String TOOL_NAME = "searchKnowledge";
    private static final int MAX_CONTENT_CHARS = 2000;

    private final AgentKnowledgeContextService knowledgeContextService;
    private final AgentToolSupport toolSupport;

    /**
     * 按用户问题检索历史业务知识、研究资料、用户文档和复盘结论。
     *
     * @param request 检索请求，只包含查询文本
     * @return 最多五个带来源的知识片段
     * @throws IllegalArgumentException 请求为空、查询文本为空或缺少服务端用户范围时抛出
     * @throws IllegalStateException 本轮已经调用过知识库工具时抛出
     */
    @Tool(name = TOOL_NAME, value = "当回答需要历史业务知识、用户资料、研究结论、策略经验或复盘案例时检索知识库；实时行情、账户、持仓和订单应使用对应实时工具")
    public KnowledgeSearchResult search(KnowledgeSearchRequest request) {
        if (request == null || !StringUtils.hasText(request.query())) {
            throw new IllegalArgumentException("知识库检索问题不能为空");
        }
        ToolExecutionContext context = AgentToolContextHolder.current();
        if (context == null || !StringUtils.hasText(context.getUserScope())) {
            throw new IllegalArgumentException("知识库工具缺少服务端用户范围");
        }
        if (!context.tryAcquireToolCall(TOOL_NAME, 1)) {
            throw new IllegalStateException("本轮知识库最多检索一次");
        }
        String userScope = context.getUserScope();
        String query = request.query().trim();
        return toolSupport.execute(TOOL_NAME, () -> search(query, userScope),
                result -> result.items().stream().map(this::evidenceRef).toList());
    }

    private KnowledgeSearchResult search(String query, String userScope) {
        try {
            List<KnowledgeSearchItem> items = knowledgeContextService.search(query, userScope).stream()
                    .map(this::toItem)
                    .toList();
            return new KnowledgeSearchResult(!items.isEmpty(), items,
                    items.isEmpty() ? "知识库中没有匹配资料" : null);
        } catch (RuntimeException exception) {
            log.warn("[Agent][RAG] mode=AUTO status=FAILED causeType={}",
                    exception.getClass().getSimpleName());
            return new KnowledgeSearchResult(false, List.of(), "知识库当前不可用");
        }
    }

    private KnowledgeSearchItem toItem(RagResult result) {
        return new KnowledgeSearchItem(
                result.getChunkId(), result.getSourceType(), result.getSourceId(), result.getVersion(),
                limit(result.getContent()), result.getScore(), result.getAsOf());
    }

    private String evidenceRef(KnowledgeSearchItem item) {
        return "rag:" + value(item.sourceType()) + ":" + value(item.sourceId()) + ":"
                + value(item.version()) + ":" + value(item.chunkId());
    }

    private String limit(String content) {
        if (content == null) {
            return "";
        }
        return content.length() <= MAX_CONTENT_CHARS
                ? content : content.substring(0, MAX_CONTENT_CHARS);
    }

    private String value(String value) {
        return value == null ? "unknown" : value.replace(':', '_');
    }

}
// AI_GENERATE_END ----