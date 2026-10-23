// AI_GENERATE_START-------
package com.stock.agent.controller;

import com.stock.agent.decision.service.AgentTradeDecisionService;
import com.stock.agent.decision.service.TradeDecisionEvaluationService;
import com.stock.agent.dto.TradeDecisionBatchResponse;
import com.stock.agent.dto.TradeDecisionEvaluationBatchResponse;
import com.stock.agent.dto.TradeDecisionGenerateRequest;
import com.stock.agent.dto.TradeDecisionProviderStatisticsResponse;
import com.stock.agent.api.ResponseDTO;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

/**
 * Agent 交易建议生成和评估控制器。
 *
 * 本控制器只管理独立交易建议，不直接调用券商或写入真实委托。
 *
 * @author mwangli
 * @since 2026-10-23
 */
@Slf4j
@RestController
@RequestMapping("/api/agent")
@RequiredArgsConstructor
public class AgentTradeDecisionController {

    private final AgentTradeDecisionService tradeDecisionService;
    private final TradeDecisionEvaluationService tradeDecisionEvaluationService;
    private final AgentRequestUserResolver requestUserResolver;

    /**
     * 生成不影响真实交易的 Agent 交易建议。
     *
     * @param request 交易建议请求
     * @param httpRequest HTTP 请求
     * @return 候选批次建议
     */
    @PostMapping("/tradeDecisions/generate")
    public ResponseDTO<TradeDecisionBatchResponse> generateTradeDecisions(
            @RequestBody TradeDecisionGenerateRequest request,
            HttpServletRequest httpRequest) {
        int maxCandidates = request == null || request.getMaxCandidates() == null
                ? 5 : request.getMaxCandidates();
        log.info("[Agent] 生成交易建议: candidateBatchId={}, maxCandidates={}",
                request == null ? null : request.getCandidateBatchId(), maxCandidates);
        requestUserResolver.requireUserScope(httpRequest);
        return ResponseDTO.success(tradeDecisionService.generate(
                request == null ? null : request.getCandidateBatchId(), maxCandidates));
    }

    /**
     * 查询候选批次已保存的交易建议。
     *
     * @param candidateBatchId 候选批次标识
     * @param httpRequest HTTP 请求
     * @return 候选批次建议
     */
    @GetMapping("/tradeDecisions/batches/{candidateBatchId}")
    public ResponseDTO<TradeDecisionBatchResponse> listTradeDecisions(
            @PathVariable String candidateBatchId,
            HttpServletRequest httpRequest) {
        log.info("[Agent] 查询候选批次交易建议: candidateBatchId={}", candidateBatchId);
        requestUserResolver.requireUserScope(httpRequest);
        return ResponseDTO.success(tradeDecisionService.listBatch(candidateBatchId));
    }

    /**
     * 查询候选批次交易建议的市场结果与反事实评估。
     *
     * @param candidateBatchId 候选批次标识
     * @param httpRequest HTTP 请求
     * @return 评估结果
     */
    @GetMapping("/tradeDecisions/evaluations")
    public ResponseDTO<TradeDecisionEvaluationBatchResponse> listTradeDecisionEvaluations(
            @RequestParam String candidateBatchId,
            HttpServletRequest httpRequest) {
        log.info("[Agent] 查询交易建议评估: candidateBatchId={}", candidateBatchId);
        requestUserResolver.requireUserScope(httpRequest);
        return ResponseDTO.success(tradeDecisionEvaluationService.listBatch(candidateBatchId));
    }

    /**
     * 查询交易建议 Provider 稳定性指标。
     *
     * @param from 统计起始时间，可为空
     * @param to 统计结束时间，可为空
     * @param httpRequest HTTP 请求
     * @return Provider 分组统计
     */
    @GetMapping("/tradeDecisions/providerStatistics")
    public ResponseDTO<TradeDecisionProviderStatisticsResponse> tradeDecisionProviderStatistics(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            HttpServletRequest httpRequest) {
        log.info("[Agent] 查询交易建议 Provider 统计: from={}, to={}", from, to);
        requestUserResolver.requireUserScope(httpRequest);
        return ResponseDTO.success(tradeDecisionEvaluationService.providerStatistics(from, to));
    }
}
// AI_GENERATE_END-------
