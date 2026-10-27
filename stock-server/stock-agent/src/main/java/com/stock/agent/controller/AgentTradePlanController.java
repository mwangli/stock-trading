// AI_GENERATE_START---------
package com.stock.agent.controller;

import com.stock.agent.dto.TradePlanCreateRequest;
import com.stock.agent.dto.TradePlanDraftRequest;
import com.stock.agent.dto.TradePlanResponse;
import com.stock.agent.dto.TradePlanReviewRequest;
import com.stock.agent.service.TradePlanAgentService;
import com.stock.agent.service.TradePlanService;
import com.stock.base.web.ResponseDTO;
import com.stock.agent.support.AgentRequestUserResolver;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Agent 交易计划草案与人工审批控制器。
 *
 * Agent 只生成和维护计划，真实交易仍由 Java 风控和执行链路决定。
 *
 * @author mwangli
 * @since 2026-10-23
 */
@Slf4j
@RestController
@RequestMapping("/api/agent")
@RequiredArgsConstructor
public class AgentTradePlanController {

    private final TradePlanService tradePlanService;
    private final TradePlanAgentService tradePlanAgentService;
    private final AgentRequestUserResolver requestUserResolver;

    /**
     * 生成结构化交易计划草案。
     *
     * @param request 草案生成请求
     * @param httpRequest HTTP 请求
     * @return 待复核交易计划
     */
    @PostMapping("/tasks/tradePlan")
    public ResponseDTO<TradePlanResponse> draftTradePlan(@RequestBody TradePlanDraftRequest request,
                                                         HttpServletRequest httpRequest) {
        log.info("[Agent] 生成交易计划草案: candidateBatchId={}",
                request == null ? null : request.getCandidateBatchId());
        return ResponseDTO.success(tradePlanAgentService.generate(
                request, requestUserResolver.requireUserScope(httpRequest)));
    }

    /**
     * 创建等待人工审批的交易计划。
     *
     * @param request 创建请求
     * @param httpRequest HTTP 请求
     * @return 交易计划
     */
    @PostMapping("/tradePlans")
    public ResponseDTO<TradePlanResponse> createTradePlan(@RequestBody TradePlanCreateRequest request,
                                                          HttpServletRequest httpRequest) {
        log.info("[Agent] 创建待审批交易计划: candidateBatchId={}",
                request == null ? null : request.getCandidateBatchId());
        return ResponseDTO.success(tradePlanService.create(
                request, requestUserResolver.requireUserScope(httpRequest)));
    }

    /**
     * 查询当前用户的交易计划。
     *
     * @param planId 计划标识
     * @param httpRequest HTTP 请求
     * @return 交易计划
     */
    @GetMapping("/tradePlans/details/{planId}")
    public ResponseDTO<TradePlanResponse> getTradePlan(@PathVariable String planId,
                                                       HttpServletRequest httpRequest) {
        log.info("[Agent] 查询交易计划: planId={}", planId);
        return ResponseDTO.success(tradePlanService.get(
                planId, requestUserResolver.requireUserScope(httpRequest)));
    }

    /**
     * 人工批准交易计划。
     *
     * @param planId 计划标识
     * @param request 审批请求
     * @param httpRequest HTTP 请求
     * @return 批准后的交易计划
     */
    @PostMapping("/tradePlans/approve/{planId}")
    public ResponseDTO<TradePlanResponse> approveTradePlan(
            @PathVariable String planId,
            @RequestBody TradePlanReviewRequest request,
            HttpServletRequest httpRequest) {
        log.info("[Agent] 人工批准交易计划: planId={}, version={}",
                planId, request == null ? null : request.getVersion());
        return ResponseDTO.success(tradePlanService.approve(
                planId, request, requestUserResolver.requireUserScope(httpRequest)));
    }

    /**
     * 人工拒绝交易计划。
     *
     * @param planId 计划标识
     * @param request 拒绝请求
     * @param httpRequest HTTP 请求
     * @return 拒绝后的交易计划
     */
    @PostMapping("/tradePlans/reject/{planId}")
    public ResponseDTO<TradePlanResponse> rejectTradePlan(
            @PathVariable String planId,
            @RequestBody TradePlanReviewRequest request,
            HttpServletRequest httpRequest) {
        log.info("[Agent] 人工拒绝交易计划: planId={}, version={}",
                planId, request == null ? null : request.getVersion());
        return ResponseDTO.success(tradePlanService.reject(
                planId, request, requestUserResolver.requireUserScope(httpRequest)));
    }

}
// AI_GENERATE_END--------