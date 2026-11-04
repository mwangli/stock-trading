// AI_GENERATE_START ---
package com.stock.console.agent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stock.console.facts.TradingFacts;
import com.stock.console.market.MarketRepository;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class AgentReadTools {

    private static final Set<String> PERFORMANCE_GROUPS = Set.of(
            "total", "day", "month", "instrument", "model");

    private final TradingFacts facts;
    private final MarketRepository market;
    private final ObjectMapper objectMapper;
    private final AgentToolAudit audit;
    private final SensitiveDataMasker masker;

    public AgentReadTools(
            TradingFacts facts,
            MarketRepository market,
            ObjectMapper objectMapper,
            AgentToolAudit audit,
            SensitiveDataMasker masker) {
        this.facts = facts;
        this.market = market;
        this.objectMapper = objectMapper;
        this.audit = audit;
        this.masker = masker;
    }

    @Tool("按总计、日、月、证券或模型汇总已完成闭环交易的毛收益、净收益和费用。只读。")
    public String performanceSummary(
            @P(value = "开始日期，格式 YYYY-MM-DD", required = false) String startDate,
            @P(value = "结束日期，格式 YYYY-MM-DD", required = false) String endDate,
            @P("分组方式: total, day, month, instrument, model") String groupBy) {
        LocalDate start = dateOrDefault(startDate, LocalDate.of(2000, 1, 1));
        LocalDate end = dateOrDefault(endDate, LocalDate.now());
        if (end.isBefore(start)) {
            throw new IllegalArgumentException("结束日期不能早于开始日期");
        }
        String normalizedGroup = groupBy == null ? "total" : groupBy.trim().toLowerCase();
        if (!PERFORMANCE_GROUPS.contains(normalizedGroup)) {
            throw new IllegalArgumentException("不支持的收益分组: " + groupBy);
        }
        audit.record("performanceSummary", arguments(
                "startDate", start.toString(), "endDate", end.toString(), "groupBy", normalizedGroup),
                "sqlite:realized_trade:performance");
        return json(facts.agentPerformanceSummary(start, end, normalizedGroup));
    }

    @Tool("查询已完成买入卖出闭环的交易明细。只读，不包含未卖出持仓。")
    public String realizedTradeDetail(
            @P(value = "开始日期，格式 YYYY-MM-DD", required = false) String startDate,
            @P(value = "结束日期，格式 YYYY-MM-DD", required = false) String endDate,
            @P(value = "证券代码，例如 sh.600000；留空表示全部", required = false) String instrument,
            @P(value = "返回条数，1 到 200", required = false) Integer limit) {
        LocalDate start = dateOrDefault(startDate, LocalDate.of(2000, 1, 1));
        LocalDate end = dateOrDefault(endDate, LocalDate.now());
        if (end.isBefore(start)) {
            throw new IllegalArgumentException("结束日期不能早于开始日期");
        }
        int resolvedLimit = limit(limit, 50, 200);
        audit.record("realizedTradeDetail", arguments(
                "startDate", start.toString(), "endDate", end.toString(),
                "instrument", instrument, "limit", resolvedLimit),
                "sqlite:realized_trade:detail");
        return json(facts.agentRealizedTrades(start, end, instrument, resolvedLimit));
    }

    @Tool("查询模型版本、近期评估和重训请求。只读。")
    public String modelSnapshot(@P(value = "返回条数，1 到 50", required = false) Integer limit) {
        int resolvedLimit = limit(limit, 10, 50);
        audit.record("modelSnapshot", arguments("limit", resolvedLimit),
                "sqlite:model_version+model_evaluation+training_request");
        return json(Map.of(
                "models", facts.models(resolvedLimit),
                "evaluations", facts.evaluations(resolvedLimit),
                "trainingRequests", facts.trainingRequests(resolvedLimit)));
    }

    @Tool("查询指定预测日的股票排名；日期留空时返回最新排名。只读。")
    public String predictionRanking(
            @P(value = "预测日期，格式 YYYY-MM-DD；留空表示最新", required = false) String predictionDate,
            @P(value = "返回条数，1 到 200", required = false) Integer limit) {
        LocalDate date = optionalDate(predictionDate);
        int resolvedLimit = limit(limit, 30, 200);
        audit.record("predictionRanking", arguments(
                "predictionDate", date == null ? null : date.toString(), "limit", resolvedLimit),
                "sqlite:prediction:ranking");
        return json(facts.agentPredictionRanking(date, resolvedLimit));
    }

    @Tool("查询最近失败的交易系统任务和错误摘要。只读。")
    public String taskFailures(@P(value = "返回条数，1 到 100", required = false) Integer limit) {
        int resolvedLimit = limit(limit, 20, 100);
        audit.record("taskFailures", arguments("limit", resolvedLimit), "sqlite:task_run:failures");
        return json(facts.agentTaskFailures(resolvedLimit));
    }

    @Tool("查询指定业务日期和任务类型的运行状态；条件留空时查询最近任务。只读。")
    public String taskStatus(
            @P(value = "业务日期，格式 YYYY-MM-DD；留空表示不限", required = false) String businessDate,
            @P(value = "任务类型关键词，例如 collect、rank、train；留空表示不限", required = false) String taskType,
            @P(value = "返回条数，1 到 100", required = false) Integer limit) {
        LocalDate date = optionalDate(businessDate);
        int resolvedLimit = limit(limit, 20, 100);
        audit.record("taskStatus", arguments(
                "businessDate", date == null ? null : date.toString(),
                "taskType", taskType, "limit", resolvedLimit),
                "sqlite:task_run:status");
        return json(facts.agentTaskStatus(date, taskType, resolvedLimit));
    }

    @Tool("查询市场数据状态、涨跌统计和最新日收益排名。只读。")
    public String marketSnapshot(@P(value = "排名条数，1 到 100", required = false) Integer limit) {
        int resolvedLimit = limit(limit, 30, 100);
        audit.record("marketSnapshot", arguments("limit", resolvedLimit), "parquet:market:summary");
        return json(Map.of(
                "source", market.status(),
                "statistics", market.statistics(),
                "dailyRanking", market.latestDailyRanking(resolvedLimit)));
    }

    @Tool("生成交易决策所需的只读证据包。只能分析已有候选，不能下单、改排名或写交易数据。")
    public String tradeDecisionEvidence(
            @P(value = "候选数量，1 到 20", required = false) Integer limit) {
        int resolvedLimit = limit(limit, 10, 20);
        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("authority", "ADVISORY_ONLY");
        evidence.put("constraints", List.of(
                "Java Agent 只能分析 Python 已生成的候选",
                "不得新增证券、生成委托字段或调用券商",
                "最终交易由 Python 核心重新校验并执行"));
        evidence.put("tradingStatus", facts.tradingStatus());
        evidence.put("models", facts.models(5));
        evidence.put("evaluations", facts.evaluations(5));
        evidence.put("candidates", facts.agentPredictionRanking(null, resolvedLimit));
        evidence.put("market", market.statistics());
        audit.record("tradeDecisionEvidence", arguments("limit", resolvedLimit),
                "sqlite+parquet:trade-decision-evidence");
        return json(evidence);
    }

    private LocalDate dateOrDefault(String value, LocalDate defaultValue) {
        LocalDate parsed = optionalDate(value);
        return parsed == null ? defaultValue : parsed;
    }

    private LocalDate optionalDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(value.trim());
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("日期格式必须为 YYYY-MM-DD");
        }
    }

    private int limit(Integer value, int defaultValue, int maximum) {
        int resolved = value == null ? defaultValue : value;
        if (resolved < 1 || resolved > maximum) {
            throw new IllegalArgumentException("返回条数必须在 1 到 " + maximum + " 之间");
        }
        return resolved;
    }

    private String json(Object value) {
        try {
            return masker.mask(objectMapper.writeValueAsString(value));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Agent 工具结果序列化失败", exception);
        }
    }

    private Map<String, Object> arguments(Object... pairs) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int index = 0; index < pairs.length; index += 2) {
            if (pairs[index + 1] != null) {
                result.put(String.valueOf(pairs[index]), pairs[index + 1]);
            }
        }
        return result;
    }
}
// AI_GENERATE_END ---
