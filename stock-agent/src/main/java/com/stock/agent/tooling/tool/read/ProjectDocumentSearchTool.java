// AI_GENERATE_START --
package com.stock.agent.tooling.tool.read;

import com.stock.agent.knowledge.document.service.ProjectDocumentSearchService;
import com.stock.agent.tooling.tool.AgentToolSupport;
import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 项目 Markdown 文档只读检索工具。
 *
 * @author mwangli
 * @since 2026-10-25
 */
@Component
@RequiredArgsConstructor
public class ProjectDocumentSearchTool {

    private final ProjectDocumentSearchService documentSearchService;
    private final AgentToolSupport toolSupport;

    /**
     * 搜索项目架构、模块设计和运行边界文档。
     *
     * @param request 文档检索请求
     * @return 最多五个相关章节
     */
    @Tool(name = "searchProjectDocuments", value = "搜索当前股票交易项目的架构、模块设计、运行边界和工程文档")
    public ProjectDocumentSearchResult search(ProjectDocumentSearchRequest request) {
        if (request == null || !StringUtils.hasText(request.query())) {
            throw new IllegalArgumentException("项目文档检索词不能为空");
        }
        return toolSupport.execute("searchProjectDocuments", () -> {
            List<ProjectDocumentSearchService.DocumentSearchResult> items =
                    documentSearchService.search(request.query().trim(), 5);
            return new ProjectDocumentSearchResult(!items.isEmpty(), items,
                    items.isEmpty() ? "未找到匹配的项目文档章节" : null);
        }, result -> result.items().stream()
                .map(ProjectDocumentSearchService.DocumentSearchResult::path)
                .toList());
    }

    /** 项目文档检索请求。 */
    public record ProjectDocumentSearchRequest(String query) {
    }

    /** 项目文档检索结果。 */
    public record ProjectDocumentSearchResult(
            boolean available,
            List<ProjectDocumentSearchService.DocumentSearchResult> items,
            String message) {
    }
}
// AI_GENERATE_END --
