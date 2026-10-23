// AI_GENERATE_START ----
package com.stock.agent.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stock.agent.client.dto.BackendFactsEnvelope;
import com.stock.agent.client.dto.BackendTradeDecisionContextFact;
import com.stock.agent.client.dto.BackendTradeDecisionContextRequest;
import com.stock.agent.client.dto.BrokerFacts;
import com.stock.agent.client.dto.BrokerStatusFact;
import com.stock.agent.client.dto.JobDiagnosticFact;
import com.stock.agent.client.dto.LatestPriceFact;
import com.stock.agent.client.dto.ModelOverviewFact;
import com.stock.agent.client.dto.PerformanceOverviewFact;
import com.stock.agent.client.dto.PredictionCandidateFact;
import com.stock.agent.client.dto.StrategyStateFact;
import com.stock.agent.client.dto.TradeExecutionGateFact;
import com.stock.agent.client.dto.TradeDecisionEvaluationFactRequest;
import com.stock.agent.client.dto.TradeDecisionEvaluationFacts;
import com.stock.agent.client.dto.TradeDecisionEvaluationFactsRequest;
import com.stock.agent.client.dto.TradingCalendarFact;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;
import java.util.List;

/**
 * Agent 访问 Backend 内部只读事实的唯一客户端。
 *
 * @author mwangli
 * @since 2026-10-23
 */
@Component
public class BackendFactsClient {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    /**
     * 创建 Backend 事实客户端。
     *
     * @param restClient Backend 专用 RestClient
     * @param objectMapper JSON 转换器
     */
    public BackendFactsClient(@Qualifier("backendFactsRestClient") RestClient restClient,
                              ObjectMapper objectMapper) {
        this.restClient = restClient;
        this.objectMapper = objectMapper;
    }

    /**
     * 查询最新日线事实。
     *
     * @param stockCode 股票代码
     * @return 最新日线事实
     */
    public LatestPriceFact latestPrice(String stockCode) {
        return get("/api/internal/agentFacts/latestPrice?stockCode={stockCode}",
                LatestPriceFact.class, stockCode);
    }

    /**
     * 查询交易日历事实。
     *
     * @param tradeDate 查询日期
     * @return 交易日历事实
     */
    public TradingCalendarFact tradingCalendar(LocalDate tradeDate) {
        return get("/api/internal/agentFacts/tradingCalendar?tradeDate={tradeDate}",
                TradingCalendarFact.class, tradeDate);
    }

    /**
     * 查询策略状态事实。
     *
     * @return 策略状态事实
     */
    public StrategyStateFact strategyState() {
        return get("/api/internal/agentFacts/strategyState", StrategyStateFact.class);
    }

    /**
     * 查询指定候选批次事实。
     *
     * @param candidateBatchId 候选批次标识
     * @return 候选批次事实
     */
    public PredictionCandidateFact predictionCandidates(String candidateBatchId) {
        return get("/api/internal/agentFacts/predictionCandidates?candidateBatchId={candidateBatchId}",
                PredictionCandidateFact.class, candidateBatchId);
    }

    /**
     * 查询目标交易日最新候选批次事实。
     *
     * @param targetTradeDate 目标交易日
     * @return 最新候选批次事实
     */
    public PredictionCandidateFact latestPredictionCandidates(LocalDate targetTradeDate) {
        return get("/api/internal/agentFacts/latestPredictionCandidates?targetTradeDate={targetTradeDate}",
                PredictionCandidateFact.class, targetTradeDate);
    }

    /**
     * 查询券商状态事实。
     *
     * @return 券商状态事实
     */
    public BrokerStatusFact brokerStatus() {
        return get("/api/internal/agentFacts/brokerStatus", BrokerStatusFact.class);
    }

    /**
     * 查询券商账户事实摘要。
     *
     * @return 券商账户事实摘要
     */
    public BrokerFacts brokerFacts() {
        return get("/api/internal/agentFacts/brokerFacts", BrokerFacts.class);
    }

    /**
     * 查询收益概览事实。
     *
     * @return 收益概览事实
     */
    public PerformanceOverviewFact performanceOverview() {
        return get("/api/internal/agentFacts/performanceOverview", PerformanceOverviewFact.class);
    }

    /**
     * 查询模型运维概览事实。
     *
     * @return 模型运维概览事实
     */
    public ModelOverviewFact modelOverview() {
        return get("/api/internal/agentFacts/modelOverview", ModelOverviewFact.class);
    }

    /**
     * 查询任务诊断事实。
     *
     * @param jobName 任务名称
     * @return 任务诊断事实
     */
    public JobDiagnosticFact jobDiagnostic(String jobName) {
        return get("/api/internal/agentFacts/jobDiagnostic?jobName={jobName}",
                JobDiagnosticFact.class, jobName);
    }

    /**
     * 查询真实交易执行门禁事实。
     *
     * @return 执行门禁事实
     */
    public TradeExecutionGateFact tradeExecutionGate() {
        return get("/api/internal/agentFacts/tradeExecutionGate", TradeExecutionGateFact.class);
    }

    /**
     * 请求 Backend 构建不可变交易决策上下文。
     *
     * @param candidateBatchId 候选批次标识
     * @param maxCandidates 最大候选数量
     * @return 决策上下文事实列表
     */
    public List<BackendTradeDecisionContextFact> tradeDecisionContexts(
            String candidateBatchId, int maxCandidates) {
        BackendFactsEnvelope response = restClient.post()
                .uri("/api/internal/agentFacts/tradeDecisionContexts")
                .body(new BackendTradeDecisionContextRequest(candidateBatchId, maxCandidates))
                .retrieve().body(BackendFactsEnvelope.class);
        if (response == null || !response.success() || response.data() == null) {
            throw new IllegalStateException(response == null ? "Backend 未返回响应" : response.message());
        }
        return objectMapper.convertValue(response.data(), objectMapper.getTypeFactory()
                .constructCollectionType(List.class, BackendTradeDecisionContextFact.class));
    }

    /**
     * 批量查询影子决策评估所需权威事实。
     *
     * @param items 待查询项目
     * @return 最新行情日期和逐候选事实
     */
    public TradeDecisionEvaluationFacts tradeDecisionEvaluationFacts(
            List<TradeDecisionEvaluationFactRequest> items) {
        BackendFactsEnvelope response = restClient.post()
                .uri("/api/internal/agentFacts/tradeDecisionEvaluationFacts")
                .body(new TradeDecisionEvaluationFactsRequest(items))
                .retrieve().body(BackendFactsEnvelope.class);
        if (response == null || !response.success() || response.data() == null) {
            throw new IllegalStateException(response == null ? "Backend 未返回响应" : response.message());
        }
        try {
            return objectMapper.treeToValue(response.data(), TradeDecisionEvaluationFacts.class);
        } catch (Exception exception) {
            throw new IllegalStateException("Backend 评估事实响应解析失败", exception);
        }
    }

    private <T> T get(String uri, Class<T> type, Object... uriVariables) {
        BackendFactsEnvelope response = restClient.get().uri(uri, uriVariables)
                .retrieve().body(BackendFactsEnvelope.class);
        if (response == null || !response.success() || response.data() == null) {
            throw new IllegalStateException(response == null ? "Backend 未返回响应" : response.message());
        }
        try {
            return objectMapper.treeToValue(response.data(), type);
        } catch (Exception exception) {
            throw new IllegalStateException("Backend 内部事实响应解析失败: " + type.getSimpleName(), exception);
        }
    }
}
// AI_GENERATE_END ----
