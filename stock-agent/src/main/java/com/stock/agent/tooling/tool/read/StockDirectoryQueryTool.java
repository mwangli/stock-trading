// AI_GENERATE_START --
package com.stock.agent.tooling.tool.read;

import com.stock.agent.tooling.model.StockDirectoryFact;
import com.stock.agent.tooling.port.ToolFactQueryPort;
import com.stock.agent.tooling.tool.AgentToolSupport;
import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 股票目录、代码、名称和当前行情摘要只读工具。
 *
 * @author mwangli
 * @since 2026-10-25
 */
@Component
@RequiredArgsConstructor
public class StockDirectoryQueryTool {

    private static final int DEFAULT_LIMIT = 10;
    private static final int MAX_LIMIT = 20;

    private final ToolFactQueryPort factQueryPort;
    private final AgentToolSupport toolSupport;

    /**
     * 按股票代码或名称查询股票目录，也可在关键字为空时查看最近更新的股票摘要。
     *
     * @param request 查询参数
     * @return 股票目录事实
     */
    @Tool(name = "queryStockDirectory", value = "查询股票代码、名称、所属市场和当前行情摘要。适用于查找某只股票、按名称找代码、确认系统收录股票或查看最近更新股票；keyword 可为空，limit 最大 20")
    public StockDirectoryFact query(StockDirectoryRequest request) {
        String keyword = request == null ? null : request.keyword();
        int limit = normalizeLimit(request == null ? null : request.limit());
        return toolSupport.execute("queryStockDirectory", () -> factQueryPort.stockDirectory(keyword, limit));
    }

    private int normalizeLimit(Integer limit) {
        if (limit == null) {
            return DEFAULT_LIMIT;
        }
        return Math.max(1, Math.min(limit, MAX_LIMIT));
    }

    /**
     * 股票目录查询请求。
     *
     * @param keyword 股票代码或名称关键字，可为空
     * @param limit 最大返回数量，可为空，范围 1 到 20
     */
    public record StockDirectoryRequest(String keyword, Integer limit) {
    }
}
// AI_GENERATE_END --
