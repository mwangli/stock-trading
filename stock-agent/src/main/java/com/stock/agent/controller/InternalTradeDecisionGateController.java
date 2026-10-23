// AI_GENERATE_START -
package com.stock.agent.controller;

import com.stock.agent.api.ResponseDTO;
import com.stock.agent.decision.service.TradeDecisionGateService;
import com.stock.agent.decision.vo.TradeDecisionFusionResult;
import com.stock.agent.dto.InternalTradeDecisionGateRequest;
import com.stock.agent.dto.InternalTradeDecisionGateResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Backend 调用的 Agent 交易门禁内部接口。
 *
 * @author mwangli
 * @since 2026-10-23
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/internal/tradeDecisionGate")
public class InternalTradeDecisionGateController {

    private final TradeDecisionGateService gateService;

    /**
     * 读取已持久化 Agent 判断并执行确定性融合。
     *
     * @param request 门禁请求
     * @return 门禁结果
     */
    @PostMapping("/evaluate")
    public ResponseDTO<InternalTradeDecisionGateResponse> evaluate(
            @RequestBody InternalTradeDecisionGateRequest request) {
        log.info("内部交易门禁评估: candidateBatchId={}, stockCode={}, strategyVersion={}",
                request.candidateBatchId(), request.stockCode(), request.strategyVersion());
        TradeDecisionFusionResult fusion = gateService.evaluateAutomaticBuy(
                request.candidateBatchId(), request.stockCode(), request.strategyVersion(),
                request.baselineEligible(), request.evaluatedAt());
        return ResponseDTO.success(new InternalTradeDecisionGateResponse(
                fusion.isAutomaticBuyAllowed(), fusion.getOutcome().name(), fusion.getReason()));
    }
}
// AI_GENERATE_END -
