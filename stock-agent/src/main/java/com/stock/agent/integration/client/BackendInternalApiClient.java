// AI_GENERATE_START ----------
package com.stock.agent.integration.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stock.agent.integration.dto.BackendAdaptiveStrategyResponse;
import com.stock.agent.integration.dto.BackendBrokerHistoryResponse;
import com.stock.agent.integration.dto.BackendBrokerFactsResponse;
import com.stock.agent.integration.dto.BackendBrokerStatusResponse;
import com.stock.agent.integration.dto.BackendFactsEnvelope;
import com.stock.agent.integration.config.BackendServiceProperties;
import com.stock.agent.integration.dto.BackendJobDiagnosticResponse;
import com.stock.agent.integration.dto.BackendJobOperationsResponse;
import com.stock.agent.integration.dto.BackendLatestPriceResponse;
import com.stock.agent.integration.dto.BackendMarketDataQualityResponse;
import com.stock.agent.integration.dto.BackendModelOverviewResponse;
import com.stock.agent.integration.dto.BackendModelOperationsResponse;
import com.stock.agent.integration.dto.BackendPerformanceOverviewResponse;
import com.stock.agent.integration.dto.BackendMarketAdvancersResponse;
import com.stock.agent.integration.dto.BackendPerformanceAnalysisResponse;
import com.stock.agent.integration.dto.BackendPredictionCandidateResponse;
import com.stock.agent.integration.dto.BackendStockDirectoryResponse;
import com.stock.agent.integration.dto.BackendStrategyStateResponse;
import com.stock.agent.integration.dto.BackendSystemRuntimeResponse;
import com.stock.agent.integration.dto.BackendTradeDecisionContextResponse;
import com.stock.agent.integration.dto.BackendTradeDecisionContextRequest;
import com.stock.agent.integration.dto.BackendTradeDecisionEvaluationFactRequest;
import com.stock.agent.integration.dto.BackendTradeDecisionEvaluationFactsResponse;
import com.stock.agent.integration.dto.BackendTradeExecutionGateResponse;
import com.stock.agent.integration.dto.BackendTradingCalendarResponse;
import com.stock.agent.integration.dto.TradeDecisionEvaluationFactsRequest;
import com.stock.agent.runtime.api.FactSourceStatus;
import com.stock.agent.shared.error.FactSourceUnavailableException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.function.Supplier;

/**
 * Agent 访问 Backend 内部只读事实的唯一客户端。
 *
 * @author mwangli
 * @since 2026-10-23
 */
@Component
public class BackendInternalApiClient {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final BackendServiceProperties properties;

    /**
     * 创建 Backend 事实客户端。
     *
     * @param restClient Backend 专用 RestClient
     * @param objectMapper JSON 转换器
     */
    public BackendInternalApiClient(@Qualifier("backendFactsRestClient") RestClient restClient,
                                    ObjectMapper objectMapper,
                                    BackendServiceProperties properties) {
        this.restClient = restClient;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    /**
     * 查询最新日线事实。
     *
     * @param stockCode 股票代码
     * @return 最新日线事实
     */
    public BackendLatestPriceResponse latestPrice(String stockCode) {
        return get("/api/internal/agentFacts/latestPrice?stockCode={stockCode}",
                BackendLatestPriceResponse.class, stockCode);
    }

    /**
     * 查询交易日历事实。
     *
     * @param tradeDate 查询日期
     * @return 交易日历事实
     */
    public BackendTradingCalendarResponse tradingCalendar(LocalDate tradeDate) {
        return get("/api/internal/agentFacts/tradingCalendar?tradeDate={tradeDate}",
                BackendTradingCalendarResponse.class, tradeDate);
    }

    /**
     * 查询策略状态事实。
     *
     * @return 策略状态事实
     */
    public BackendStrategyStateResponse strategyState() {
        return get("/api/internal/agentFacts/strategyState", BackendStrategyStateResponse.class);
    }

    /**
     * 查询指定候选批次事实。
     *
     * @param candidateBatchId 候选批次标识
     * @return 候选批次事实
     */
    public BackendPredictionCandidateResponse predictionCandidates(String candidateBatchId) {
        return get("/api/internal/agentFacts/predictionCandidates?candidateBatchId={candidateBatchId}",
                BackendPredictionCandidateResponse.class, candidateBatchId);
    }

    /**
     * 查询目标交易日最新候选批次事实。
     *
     * @param targetTradeDate 目标交易日
     * @return 最新候选批次事实
     */
    public BackendPredictionCandidateResponse latestPredictionCandidates(LocalDate targetTradeDate) {
        return get("/api/internal/agentFacts/latestPredictionCandidates?targetTradeDate={targetTradeDate}",
                BackendPredictionCandidateResponse.class, targetTradeDate);
    }

    /**
     * 查询券商状态事实。
     *
     * @return 券商状态事实
     */
    public BackendBrokerStatusResponse brokerStatus() {
        return get("/api/internal/agentFacts/brokerStatus", BackendBrokerStatusResponse.class);
    }

    /**
     * 查询券商账户事实摘要。
     *
     * @return 券商账户事实摘要
     */
    public BackendBrokerFactsResponse brokerFacts() {
        return get("/api/internal/agentFacts/brokerFacts", BackendBrokerFactsResponse.class);
    }

    /**
     * 查询收益概览事实。
     *
     * @return 收益概览事实
     */
    public BackendPerformanceOverviewResponse performanceOverview() {
        return get("/api/internal/agentFacts/performanceOverview", BackendPerformanceOverviewResponse.class);
    }

    /**
     * 查询最近可用行情日的涨跌分布。
     *
     * @return 最近行情日事实
     */
    public BackendMarketAdvancersResponse marketAdvancers() {
        return get("/api/internal/agentFacts/marketAdvancers", BackendMarketAdvancersResponse.class);
    }

    /**
     * 查询指定自然月的收益分析。
     *
     * @param yearMonth 查询自然月
     * @return 月度收益分析事实
     */
    public BackendPerformanceAnalysisResponse performanceAnalysis(YearMonth yearMonth) {
        return get("/api/internal/agentFacts/performanceAnalysis?yearMonth={yearMonth}",
                BackendPerformanceAnalysisResponse.class, yearMonth.toString());
    }

    /**
     * 查询最近一个存在已实现交易的自然月收益分析。
     *
     * @return 最近有交易月份的收益分析事实
     */
    public BackendPerformanceAnalysisResponse latestPerformanceAnalysis() {
        return get("/api/internal/agentFacts/latestPerformanceAnalysis",
                BackendPerformanceAnalysisResponse.class);
    }

    /**
     * 查询模型运维概览事实。
     *
     * @return 模型运维概览事实
     */
    public BackendModelOverviewResponse modelOverview() {
        return get("/api/internal/agentFacts/modelOverview", BackendModelOverviewResponse.class);
    }

    /**
     * 查询任务诊断事实。
     *
     * @param jobName 任务名称
     * @return 任务诊断事实
     */
    public BackendJobDiagnosticResponse jobDiagnostic(String jobName) {
        return get("/api/internal/agentFacts/jobDiagnostic?jobName={jobName}",
                BackendJobDiagnosticResponse.class, jobName);
    }

    /**
     * 查询真实交易执行门禁事实。
     *
     * @return 执行门禁事实
     */
    public BackendTradeExecutionGateResponse tradeExecutionGate() {
        return get("/api/internal/agentFacts/tradeExecutionGate", BackendTradeExecutionGateResponse.class);
    }

    /**
     * 查询股票目录和当前行情摘要。
     *
     * @param keyword 股票代码或名称关键字
     * @param limit 最大返回数量
     * @return 股票目录事实
     */
    public BackendStockDirectoryResponse stockDirectory(String keyword, int limit) {
        return get("/api/internal/agentFacts/stockDirectory?keyword={keyword}&limit={limit}",
                BackendStockDirectoryResponse.class, keyword == null ? "" : keyword, limit);
    }

    /**
     * 查询市场基础数据质量。
     *
     * @return 市场数据质量事实
     */
    public BackendMarketDataQualityResponse marketDataQuality() {
        return get("/api/internal/agentFacts/marketDataQuality", BackendMarketDataQualityResponse.class);
    }

    /**
     * 查询模型版本、训练和预测运维事实。
     *
     * @param mode 查询模式
     * @param referenceId 关联批次标识
     * @param limit 最大返回数量
     * @return 模型运维事实
     */
    public BackendModelOperationsResponse modelOperations(String mode, String referenceId, int limit) {
        return get("/api/internal/agentFacts/modelOperations?mode={mode}&referenceId={referenceId}&limit={limit}",
                BackendModelOperationsResponse.class, mode, referenceId == null ? "" : referenceId, limit);
    }

    /**
     * 查询调度任务清单或运行记录。
     *
     * @param mode 查询模式
     * @param jobName 任务名称
     * @param limit 最大返回数量
     * @return 调度任务事实
     */
    public BackendJobOperationsResponse jobOperations(String mode, String jobName, int limit) {
        return get("/api/internal/agentFacts/jobOperations?mode={mode}&jobName={jobName}&limit={limit}",
                BackendJobOperationsResponse.class, mode, jobName == null ? "" : jobName, limit);
    }

    /**
     * 查询脱敏历史委托或成交。
     *
     * @param type 查询类型
     * @param startDate 开始日期
     * @param endDate 结束日期
     * @param limit 最大返回数量
     * @return 券商历史事实
     */
    public BackendBrokerHistoryResponse brokerHistory(
            String type, LocalDate startDate, LocalDate endDate, int limit) {
        return get("/api/internal/agentFacts/brokerHistory?type={type}&startDate={startDate}"
                        + "&endDate={endDate}&limit={limit}",
                BackendBrokerHistoryResponse.class, type, startDate, endDate, limit);
    }

    /**
     * 查询自适应策略生命周期和演化事实。
     *
     * @return 自适应策略事实
     */
    public BackendAdaptiveStrategyResponse adaptiveStrategy() {
        return get("/api/internal/agentFacts/adaptiveStrategy", BackendAdaptiveStrategyResponse.class);
    }

    /**
     * 查询 Backend 运行状态和只读控制状态。
     *
     * @return 系统运行事实
     */
    public BackendSystemRuntimeResponse systemRuntime() {
        return get("/api/internal/agentFacts/systemRuntime", BackendSystemRuntimeResponse.class);
    }

    /**
     * 请求 Backend 构建不可变交易决策上下文。
     *
     * @param candidateBatchId 候选批次标识
     * @param maxCandidates 最大候选数量
     * @return 决策上下文事实列表
     */
    public List<BackendTradeDecisionContextResponse> tradeDecisionContexts(
            String candidateBatchId, int maxCandidates) {
        return execute("tradeDecisionContexts", () -> {
            BackendFactsEnvelope response = restClient.post()
                    .uri("/api/internal/agentFacts/tradeDecisionContexts")
                    .body(new BackendTradeDecisionContextRequest(candidateBatchId, maxCandidates))
                    .retrieve().body(BackendFactsEnvelope.class);
            requireSuccessfulResponse(response);
            return objectMapper.convertValue(response.data(), objectMapper.getTypeFactory()
                    .constructCollectionType(List.class, BackendTradeDecisionContextResponse.class));
        });
    }

    /**
     * 批量查询交易建议反事实评估所需权威事实。
     *
     * @param items 待查询项目
     * @return 最新行情日期和逐候选事实
     */
    public BackendTradeDecisionEvaluationFactsResponse tradeDecisionEvaluationFacts(
            List<BackendTradeDecisionEvaluationFactRequest> items) {
        return execute("tradeDecisionEvaluationFacts", () -> {
            BackendFactsEnvelope response = restClient.post()
                    .uri("/api/internal/agentFacts/tradeDecisionEvaluationFacts")
                    .body(new TradeDecisionEvaluationFactsRequest(items))
                    .retrieve().body(BackendFactsEnvelope.class);
            requireSuccessfulResponse(response);
            try {
                return objectMapper.treeToValue(response.data(), BackendTradeDecisionEvaluationFactsResponse.class);
            } catch (Exception exception) {
                throw new FactSourceUnavailableException("Backend 评估事实响应解析失败", exception);
            }
        });
    }

    /**
     * 按需探测 Backend，不参与 Agent 启动门禁。
     *
     * @return Backend 事实源状态
     */
    public FactSourceStatus status() {
        if (!properties.isEnabled()) {
            return new FactSourceStatus(false, false, "DISABLED", "Backend 权威事实源已禁用");
        }
        try {
            tradingCalendar(LocalDate.now());
            return new FactSourceStatus(true, true, "AVAILABLE", "Backend 权威事实源可用");
        } catch (FactSourceUnavailableException exception) {
            return new FactSourceStatus(true, false, "UNAVAILABLE", "Backend 权威事实源不可用");
        }
    }

    private <T> T get(String uri, Class<T> type, Object... uriVariables) {
        return execute(type.getSimpleName(), () -> {
            BackendFactsEnvelope response = restClient.get().uri(uri, uriVariables)
                    .retrieve().body(BackendFactsEnvelope.class);
            requireSuccessfulResponse(response);
            try {
                return objectMapper.treeToValue(response.data(), type);
            } catch (Exception exception) {
                throw new FactSourceUnavailableException(
                        "Backend 内部事实响应解析失败: " + type.getSimpleName(), exception);
            }
        });
    }

    private void requireSuccessfulResponse(BackendFactsEnvelope response) {
        if (response == null || !response.success() || response.data() == null) {
            throw new FactSourceUnavailableException(
                    response == null ? "Backend 权威事实源未返回响应" : response.message());
        }
    }

    private <T> T execute(String operation, Supplier<T> action) {
        if (!properties.isEnabled()) {
            throw new FactSourceUnavailableException("Backend 权威事实源已禁用: " + operation);
        }
        try {
            return action.get();
        } catch (FactSourceUnavailableException exception) {
            throw exception;
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode().value() == 401 || exception.getStatusCode().value() == 403) {
                throw new FactSourceUnavailableException("Backend 内部鉴权失败: " + operation, exception);
            }
            throw new FactSourceUnavailableException(
                    "Backend 内部事实请求失败 HTTP " + exception.getStatusCode().value() + ": " + operation,
                    exception);
        } catch (RestClientException exception) {
            throw new FactSourceUnavailableException("Backend 权威事实源不可用: " + operation, exception);
        } catch (RuntimeException exception) {
            throw new FactSourceUnavailableException("Backend 权威事实调用失败: " + operation, exception);
        }
    }
}
// AI_GENERATE_END ---------
