// AI_GENERATE_START-----
package com.stock.agent.controller;

import com.stock.agent.decision.service.AgentTradeDecisionService;
import com.stock.agent.decision.service.TradeDecisionEvaluationService;
import com.stock.agent.decision.service.TradeDecisionModeService;
import com.stock.agent.dto.ShadowTradeDecisionRequest;
import com.stock.agent.dto.TradeDecisionBatchResponse;
import com.stock.agent.dto.TradeDecisionEvaluationBatchResponse;
import com.stock.agent.dto.TradeDecisionGenerateRequest;
import com.stock.agent.dto.TradeDecisionModeConfigResponse;
import com.stock.agent.dto.TradeDecisionModeFallbackRequest;
import com.stock.agent.dto.TradeDecisionModeUpdateRequest;
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
 * Agent 交易决策生成、评估和治理控制器。
 *
 * 本控制器只管理受控决策增强，不直接调用券商或写入真实委托。
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
    private final TradeDecisionModeService tradeDecisionModeService;
    private final AgentRequestUserResolver requestUserResolver;

    /**
     * 为候选批次生成不影响真实交易的 SHADOW 判断。
     *
     * @param request 影子判断请求
     * @param httpRequest HTTP 请求
     * @return 候选批次判断
     */
    @PostMapping("/tradeDecisions/shadow")
    public ResponseDTO<TradeDecisionBatchResponse> generateShadowTradeDecisions(
            @RequestBody ShadowTradeDecisionRequest request,
            HttpServletRequest httpRequest) {
        int maxCandidates = request == null || request.getMaxCandidates() == null
                ? 5 : request.getMaxCandidates();
        log.info("[Agent] 生成影子交易判断: candidateBatchId={}, maxCandidates={}",
                request == null ? null : request.getCandidateBatchId(), maxCandidates);
        requestUserResolver.requireUserScope(httpRequest);
        return ResponseDTO.success(tradeDecisionService.generateShadow(
                request == null ? null : request.getCandidateBatchId(), maxCandidates));
    }

    /**
     * 按当前治理配置生成 Agent 交易判断。
     *
     * @param request 交易判断请求
     * @param httpRequest HTTP 请求
     * @return 候选批次判断
     */
    @PostMapping("/tradeDecisions/generate")
    public ResponseDTO<TradeDecisionBatchResponse> generateTradeDecisions(
            @RequestBody TradeDecisionGenerateRequest request,
            HttpServletRequest httpRequest) {
        int maxCandidates = request == null || request.getMaxCandidates() == null
                ? 5 : request.getMaxCandidates();
        log.info("[Agent] 按治理配置生成交易判断: candidateBatchId={}, maxCandidates={}",
                request == null ? null : request.getCandidateBatchId(), maxCandidates);
        requestUserResolver.requireUserScope(httpRequest);
        return ResponseDTO.success(tradeDecisionService.generateConfigured(
                request == null ? null : request.getCandidateBatchId(), maxCandidates));
    }

    /**
     * 查询当前 Agent 交易决策参与模式。
     *
     * @param httpRequest HTTP 请求
     * @return 当前治理配置
     */
    @GetMapping("/tradeDecisionModes/current")
    public ResponseDTO<TradeDecisionModeConfigResponse> currentTradeDecisionMode(HttpServletRequest httpRequest) {
        log.info("[Agent治理] 查询交易决策模式配置");
        requestUserResolver.requireUserScope(httpRequest);
        return ResponseDTO.success(tradeDecisionModeService.current());
    }

    /**
     * 管理员更新 Agent 交易决策参与模式。
     *
     * @param request 模式更新请求
     * @param httpRequest HTTP 请求
     * @return 更新后的治理配置
     */
    @PostMapping("/tradeDecisionModes/update")
    public ResponseDTO<TradeDecisionModeConfigResponse> updateTradeDecisionMode(
            @RequestBody TradeDecisionModeUpdateRequest request,
            HttpServletRequest httpRequest) {
        log.info("[Agent治理] 更新交易决策模式: mode={}, rolloutPercentage={}, productionImpact={}",
                request == null ? null : request.getDecisionMode(),
                request == null ? null : request.getRolloutPercentage(),
                request == null ? null : request.getProductionImpactEnabled());
        return ResponseDTO.success(tradeDecisionModeService.update(
                request, requestUserResolver.requireAdminOperator(httpRequest)));
    }

    /**
     * 管理员将 Agent 交易决策立即回退到 SHADOW。
     *
     * @param request 模式回退请求
     * @param httpRequest HTTP 请求
     * @return 回退后的治理配置
     */
    @PostMapping("/tradeDecisionModes/fallback")
    public ResponseDTO<TradeDecisionModeConfigResponse> fallbackTradeDecisionMode(
            @RequestBody TradeDecisionModeFallbackRequest request,
            HttpServletRequest httpRequest) {
        log.info("[Agent治理] 请求回退交易决策模式: reason={}",
                request == null ? null : request.getReason());
        return ResponseDTO.success(tradeDecisionModeService.fallback(
                request, requestUserResolver.requireAdminOperator(httpRequest)));
    }

    /**
     * 查询候选批次已保存的交易判断。
     *
     * @param candidateBatchId 候选批次标识
     * @param httpRequest HTTP 请求
     * @return 候选批次判断
     */
    @GetMapping("/tradeDecisions/batches/{candidateBatchId}")
    public ResponseDTO<TradeDecisionBatchResponse> listTradeDecisions(
            @PathVariable String candidateBatchId,
            HttpServletRequest httpRequest) {
        log.info("[Agent] 查询候选批次交易判断: candidateBatchId={}", candidateBatchId);
        requestUserResolver.requireUserScope(httpRequest);
        return ResponseDTO.success(tradeDecisionService.listBatch(candidateBatchId));
    }

    /**
     * 查询候选批次的市场结果与反事实评估。
     *
     * @param candidateBatchId 候选批次标识
     * @param httpRequest HTTP 请求
     * @return 评估结果
     */
    @GetMapping("/tradeDecisions/evaluations")
    public ResponseDTO<TradeDecisionEvaluationBatchResponse> listTradeDecisionEvaluations(
            @RequestParam String candidateBatchId,
            HttpServletRequest httpRequest) {
        log.info("[Agent] 查询交易判断评估: candidateBatchId={}", candidateBatchId);
        requestUserResolver.requireUserScope(httpRequest);
        return ResponseDTO.success(tradeDecisionEvaluationService.listBatch(candidateBatchId));
    }

    /**
     * 查询交易判断 Provider 稳定性指标。
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
        log.info("[Agent] 查询交易判断 Provider 统计: from={}, to={}", from, to);
        requestUserResolver.requireUserScope(httpRequest);
        return ResponseDTO.success(tradeDecisionEvaluationService.providerStatistics(from, to));
    }
}
// AI_GENERATE_END-----
