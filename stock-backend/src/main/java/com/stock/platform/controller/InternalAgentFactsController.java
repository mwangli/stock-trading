// AI_GENERATE_START------
package com.stock.platform.controller;

import com.stock.platform.dto.InternalBrokerFactsResponse;
import com.stock.platform.dto.InternalBrokerStatusResponse;
import com.stock.platform.dto.InternalJobDiagnosticResponse;
import com.stock.platform.dto.InternalLatestPriceResponse;
import com.stock.platform.dto.InternalModelOverviewResponse;
import com.stock.platform.dto.InternalPerformanceOverviewResponse;
import com.stock.platform.dto.InternalMarketAdvancersResponse;
import com.stock.platform.dto.InternalPerformanceAnalysisResponse;
import com.stock.platform.dto.InternalPredictionCandidateResponse;
import com.stock.platform.dto.InternalStrategyStateResponse;
import com.stock.platform.dto.InternalTradeExecutionGateResponse;
import com.stock.platform.dto.InternalTradingCalendarResponse;
import com.stock.platform.dto.InternalTradeDecisionContextRequest;
import com.stock.platform.dto.InternalTradeDecisionContextResponse;
import com.stock.platform.dto.InternalTradeDecisionEvaluationFactsRequest;
import com.stock.platform.dto.InternalTradeDecisionEvaluationFactsResponse;
import com.stock.platform.service.InternalAgentFactsService;
import com.stock.platform.service.InternalTradeDecisionContextService;
import com.stock.platform.service.InternalTradeDecisionEvaluationFactsService;
import com.stock.platform.dto.ResponseDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

/**
 * Agent 使用的 Backend 内部只读事实接口。
 *
 * @author mwangli
 * @since 2026-10-23
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/internal/agentFacts")
public class InternalAgentFactsController {

    private final InternalAgentFactsService factsService;
    private final InternalTradeDecisionContextService contextService;
    private final InternalTradeDecisionEvaluationFactsService evaluationFactsService;

    /**
     * 查询股票最新日线事实。
     *
     * @param stockCode 股票代码
     * @return 最新日线事实
     */
    @GetMapping("/latestPrice")
    public ResponseDTO<InternalLatestPriceResponse> latestPrice(@RequestParam String stockCode) {
        log.info("Agent 内部事实查询: latestPrice, stockCode={}", stockCode);
        return ResponseDTO.success(factsService.latestPrice(stockCode));
    }

    /**
     * 查询交易日历事实。
     *
     * @param tradeDate 查询日期
     * @return 交易日历事实
     */
    @GetMapping("/tradingCalendar")
    public ResponseDTO<InternalTradingCalendarResponse> tradingCalendar(@RequestParam LocalDate tradeDate) {
        log.info("Agent 内部事实查询: tradingCalendar, tradeDate={}", tradeDate);
        return ResponseDTO.success(factsService.tradingCalendar(tradeDate));
    }

    /**
     * 查询策略状态。
     *
     * @return 策略状态
     */
    @GetMapping("/strategyState")
    public ResponseDTO<InternalStrategyStateResponse> strategyState() {
        log.info("Agent 内部事实查询: strategyState");
        return ResponseDTO.success(factsService.strategyState());
    }

    /**
     * 查询指定候选批次。
     *
     * @param candidateBatchId 候选批次标识
     * @return 候选批次摘要
     */
    @GetMapping("/predictionCandidates")
    public ResponseDTO<InternalPredictionCandidateResponse> predictionCandidates(
            @RequestParam String candidateBatchId) {
        log.info("Agent 内部事实查询: predictionCandidates, candidateBatchId={}", candidateBatchId);
        return ResponseDTO.success(factsService.predictionCandidates(candidateBatchId));
    }

    /**
     * 查询目标交易日最新候选批次。
     *
     * @param targetTradeDate 目标交易日
     * @return 最新候选批次摘要
     */
    @GetMapping("/latestPredictionCandidates")
    public ResponseDTO<InternalPredictionCandidateResponse> latestPredictionCandidates(
            @RequestParam LocalDate targetTradeDate) {
        log.info("Agent 内部事实查询: latestPredictionCandidates, targetTradeDate={}", targetTradeDate);
        return ResponseDTO.success(factsService.latestPredictionCandidates(targetTradeDate));
    }

    /**
     * 查询券商状态。
     *
     * @return 券商状态
     */
    @GetMapping("/brokerStatus")
    public ResponseDTO<InternalBrokerStatusResponse> brokerStatus() {
        log.info("Agent 内部事实查询: brokerStatus");
        return ResponseDTO.success(factsService.brokerStatus());
    }

    /**
     * 查询券商账户事实摘要。
     *
     * @return 券商账户事实摘要
     */
    @GetMapping("/brokerFacts")
    public ResponseDTO<InternalBrokerFactsResponse> brokerFacts() {
        log.info("Agent 内部事实查询: brokerFacts");
        return ResponseDTO.success(factsService.brokerFacts());
    }

    /**
     * 查询收益概览。
     *
     * @return 收益概览
     */
    @GetMapping("/performanceOverview")
    public ResponseDTO<InternalPerformanceOverviewResponse> performanceOverview() {
        log.info("Agent 内部事实查询: performanceOverview");
        return ResponseDTO.success(factsService.performanceOverview());
    }

    /**
     * 查询最近可用行情日的涨跌分布和涨幅榜。
     *
     * @return 最近行情日事实
     */
    @GetMapping("/marketAdvancers")
    public ResponseDTO<InternalMarketAdvancersResponse> marketAdvancers() {
        log.info("Agent 内部事实查询: marketAdvancers");
        return ResponseDTO.success(factsService.marketAdvancers());
    }

    /**
     * 查询指定自然月的已实现收益分析。
     *
     * @param yearMonth 查询自然月，格式 yyyy-MM
     * @return 月度收益分析事实
     */
    @GetMapping("/performanceAnalysis")
    public ResponseDTO<InternalPerformanceAnalysisResponse> performanceAnalysis(
            @RequestParam YearMonth yearMonth) {
        log.info("Agent 内部事实查询: performanceAnalysis, yearMonth={}", yearMonth);
        return ResponseDTO.success(factsService.performanceAnalysis(yearMonth));
    }

    /**
     * 查询模型运维概览。
     *
     * @return 模型运维概览
     */
    @GetMapping("/modelOverview")
    public ResponseDTO<InternalModelOverviewResponse> modelOverview() {
        log.info("Agent 内部事实查询: modelOverview");
        return ResponseDTO.success(factsService.modelOverview());
    }

    /**
     * 查询任务诊断摘要。
     *
     * @param jobName 任务名称
     * @return 任务诊断摘要
     */
    @GetMapping("/jobDiagnostic")
    public ResponseDTO<InternalJobDiagnosticResponse> jobDiagnostic(@RequestParam String jobName) {
        log.info("Agent 内部事实查询: jobDiagnostic, jobName={}", jobName);
        return ResponseDTO.success(factsService.jobDiagnostic(jobName));
    }

    /**
     * 查询真实交易执行门禁。
     *
     * @return 执行门禁摘要
     */
    @GetMapping("/tradeExecutionGate")
    public ResponseDTO<InternalTradeExecutionGateResponse> tradeExecutionGate() {
        log.info("Agent 内部事实查询: tradeExecutionGate");
        return ResponseDTO.success(factsService.tradeExecutionGate());
    }

    /**
     * 在 Backend 权威事实边界内构建决策上下文。
     *
     * @param request 上下文构建参数
     * @return 不可变决策上下文列表
     */
    @PostMapping("/tradeDecisionContexts")
    public ResponseDTO<List<InternalTradeDecisionContextResponse>> tradeDecisionContexts(
            @RequestBody InternalTradeDecisionContextRequest request) {
        log.info("Agent 内部事实查询: tradeDecisionContexts, candidateBatchId={}, maxCandidates={}",
                request.candidateBatchId(), request.maxCandidates());
        return ResponseDTO.success(contextService.buildBatch(
                request.candidateBatchId(), request.maxCandidates()));
    }

    /**
     * 批量查询交易建议评估所需权威事实。
     *
     * @param request 待查询项目
     * @return 最新行情日期和逐候选事实
     */
    @PostMapping("/tradeDecisionEvaluationFacts")
    public ResponseDTO<InternalTradeDecisionEvaluationFactsResponse> tradeDecisionEvaluationFacts(
            @RequestBody InternalTradeDecisionEvaluationFactsRequest request) {
        int size = request.items() == null ? 0 : request.items().size();
        log.info("Agent 内部事实查询: tradeDecisionEvaluationFacts, itemCount={}", size);
        return ResponseDTO.success(evaluationFactsService.query(request.items()));
    }
}
// AI_GENERATE_END------
