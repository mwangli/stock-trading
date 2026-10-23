// AI_GENERATE_START--------
package com.stock.agent.decision.job;

import com.stock.data.service.TradingCalendarService;
import com.stock.agent.dto.TradeDecisionBatchResponse;
import com.stock.agent.decision.service.AgentTradeDecisionService;
import com.stock.agent.decision.service.TradeDecisionEvaluationService;
import com.stock.strategy.entity.StockRanking;
import com.stock.strategy.repository.RankingRepository;
import com.stock.base.job.JobSkippedException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * Agent 交易判断调度编排服务。
 * 负责按治理配置解析当前候选批次和触发评估，不接入券商适配器。
 *
 * @author mwangli
 * @since 2026-10-23
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AgentTradeDecisionJobCoordinator {

    private static final int DEFAULT_SHADOW_CANDIDATES = 10;
    private static final int DEFAULT_EVALUATION_ITEMS = 200;

    private final TradingCalendarService tradingCalendarService;
    private final RankingRepository rankingRepository;
    private final AgentTradeDecisionService decisionService;
    private final TradeDecisionEvaluationService evaluationService;

    /**
     * 为当前目标交易日的最新候选批次按治理配置生成判断。
     *
     * @throws JobSkippedException 当当前目标交易日没有候选批次时抛出
     */
    public void generateCurrentShadowDecisions() {
        LocalDate today = LocalDate.now();
        LocalDate targetTradeDate = tradingCalendarService.isTradingDay(today)
                ? today : tradingCalendarService.nextTradingDay(today);
        StockRanking latest = rankingRepository
                .findFirstByTargetTradeDateOrderByCalculateTimeDesc(targetTradeDate)
                .orElseThrow(() -> new JobSkippedException(
                        "当前目标交易日没有可用候选批次: " + targetTradeDate));
        TradeDecisionBatchResponse result = decisionService.generateConfigured(
                latest.getCandidateBatchId(), DEFAULT_SHADOW_CANDIDATES);
        if (result.getTotal() == 0) {
            throw new JobSkippedException("当前模式未生成候选判断: "
                    + latest.getCandidateBatchId());
        }
        log.info("Agent 交易判断调度完成: targetTradeDate={}, candidateBatchId={}, total={}",
                targetTradeDate, latest.getCandidateBatchId(), result.getTotal());
    }

    /**
     * 评估行情已经到达的历史 SHADOW 判断。
     *
     * @throws JobSkippedException 当没有可更新的判断时抛出
     */
    public void evaluatePendingShadowDecisions() {
        int evaluated = evaluationService.evaluatePending(DEFAULT_EVALUATION_ITEMS);
        if (evaluated == 0) {
            throw new JobSkippedException("当前没有满足行情条件的 Agent 影子判断需要评估");
        }
        log.info("Agent 影子判断评估调度完成: evaluated={}", evaluated);
    }
}
// AI_GENERATE_END--------
