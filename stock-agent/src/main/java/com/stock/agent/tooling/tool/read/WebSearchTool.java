// AI_GENERATE_START --
package com.stock.agent.tooling.tool.read;

import com.stock.agent.integration.client.DeepSeekWebSearchClient;
import com.stock.agent.tooling.tool.AgentToolSupport;
import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 公开网页搜索只读工具。
 *
 * @author mwangli
 * @since 2026-10-25
 */
@Component
@RequiredArgsConstructor
public class WebSearchTool {

    private final DeepSeekWebSearchClient searchClient;
    private final AgentToolSupport toolSupport;

    /**
     * 查询天气等近期公开信息。
     *
     * @param request 搜索请求
     * @return 最多五条带来源的搜索结果
     */
    @Tool(name = "searchWeb", value = "搜索天气、近期公开信息和项目之外的实时事实，返回带来源的摘要")
    public WebSearchResult search(WebSearchRequest request) {
        if (request == null || !StringUtils.hasText(request.query())) {
            throw new IllegalArgumentException("网页搜索问题不能为空");
        }
        return toolSupport.execute("searchWeb", () -> {
            List<DeepSeekWebSearchClient.WebSearchItem> items = searchClient.search(request.query().trim());
            return new WebSearchResult(!items.isEmpty(), LocalDateTime.now(), items,
                    items.isEmpty() ? "搜索成功但未返回匹配结果" : null);
        }, result -> result.items().stream()
                .map(DeepSeekWebSearchClient.WebSearchItem::url)
                .toList());
    }

    /** 网页搜索请求。 */
    public record WebSearchRequest(String query) {
    }

    /** 网页搜索结果。 */
    public record WebSearchResult(boolean available, LocalDateTime searchedAt,
                                  List<DeepSeekWebSearchClient.WebSearchItem> items, String message) {
    }
}
// AI_GENERATE_END --
