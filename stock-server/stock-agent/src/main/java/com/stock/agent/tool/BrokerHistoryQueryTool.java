// AI_GENERATE_START ---
package com.stock.agent.tool;

import com.stock.agent.model.BrokerHistoryFact;
import com.stock.agent.service.AgentFactQueryService;
import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * 脱敏券商历史委托和成交只读工具。
 *
 * @author mwangli
 * @since 2026-10-25
 */
@Component
@RequiredArgsConstructor
public class BrokerHistoryQueryTool {

    private static final int DEFAULT_LIMIT = 10;
    private static final int MAX_LIMIT = 20;
    private static final long MAX_DATE_SPAN_DAYS = 30;

    private final AgentFactQueryService factQueryService;
    private final AgentToolSupport toolSupport;

    /**
     * 查询不超过 31 个自然日的历史委托或成交摘要。
     *
     * @param request 查询参数
     * @return 券商历史事实
     */
    @Tool(name = "queryBrokerHistory", value = "查询脱敏券商历史记录。type=ORDERS 查询委托状态、价格和数量，type=FILLS 查询实际成交；startDate 和 endDate 必填且最多覆盖 31 个自然日，limit 最大 20")
    public BrokerHistoryFact query(BrokerHistoryRequest request) {
        validate(request);
        BrokerHistoryType type = request.type() == null ? BrokerHistoryType.ORDERS : request.type();
        int limit = normalizeLimit(request.limit());
        return toolSupport.execute("queryBrokerHistory", () -> factQueryService.brokerHistory(
                type.name(), request.startDate(), request.endDate(), limit));
    }

    private void validate(BrokerHistoryRequest request) {
        if (request == null || request.startDate() == null || request.endDate() == null) {
            throw new IllegalArgumentException("startDate 和 endDate 不能为空");
        }
        if (request.endDate().isBefore(request.startDate())) {
            throw new IllegalArgumentException("endDate 不能早于 startDate");
        }
        if (ChronoUnit.DAYS.between(request.startDate(), request.endDate()) > MAX_DATE_SPAN_DAYS) {
            throw new IllegalArgumentException("历史查询最多覆盖 31 个自然日");
        }
    }

    private int normalizeLimit(Integer limit) {
        if (limit == null) {
            return DEFAULT_LIMIT;
        }
        return Math.max(1, Math.min(limit, MAX_LIMIT));
    }

    /** 券商历史查询类型。 */
    public enum BrokerHistoryType {
        ORDERS,
        FILLS
    }

    /**
     * 券商历史查询请求。
     *
     * @param type 历史类型，可为空，默认查询委托
     * @param startDate 开始日期，包含当天
     * @param endDate 结束日期，包含当天
     * @param limit 最大返回数量，可为空，范围 1 到 20
     */
    public record BrokerHistoryRequest(
            BrokerHistoryType type, LocalDate startDate, LocalDate endDate, Integer limit) {
    }
}
// AI_GENERATE_END ---