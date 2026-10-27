// AI_GENERATE_START-------------
package com.stock.agent.tool;

import com.stock.agent.service.AgentFactQueryService;
import com.stock.agent.model.PredictionCandidateFact;
import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.List;

/**
 * 策略候选批次只读工具。
 *
 * @author mwangli
 * @since 2026-10-17
 */
@Component
@RequiredArgsConstructor
public class PredictionCandidateQueryTool {

    private final AgentFactQueryService factQueryService;
    private final AgentToolSupport toolSupport;

    /**
     * 查询指定候选批次的脱敏排名摘要。
     *
     * @param request 查询请求
     * @return 候选批次摘要
     */
    @Tool(name = "queryPredictionCandidates", value = "查询指定候选批次的脱敏排名摘要")
    public PredictionCandidateResult query(PredictionCandidateRequest request) {
        if (request == null || !StringUtils.hasText(request.candidateBatchId())) {
            throw new IllegalArgumentException("candidateBatchId 不能为空");
        }
        return toolSupport.execute("queryPredictionCandidates", () -> {
            PredictionCandidateFact fact = factQueryService
                    .predictionCandidates(request.candidateBatchId().trim());
            return toResult(fact);
        });
    }

    /**
     * 查询指定目标交易日对应的最新候选批次。
     *
     * @param targetTradeDate 目标交易日
     * @return 最新候选批次摘要；没有候选时返回空摘要
     */
    public PredictionCandidateResult queryLatest(LocalDate targetTradeDate) {
        if (targetTradeDate == null) {
            throw new IllegalArgumentException("targetTradeDate 不能为空");
        }
        return toolSupport.execute("queryLatestPredictionCandidate",
                () -> toResult(factQueryService.latestPredictionCandidates(targetTradeDate)));
    }

    private PredictionCandidateResult toResult(PredictionCandidateFact fact) {
        List<PredictionCandidateItem> items = fact.items().stream()
                .map(item -> new PredictionCandidateItem(item.stockCode(), item.stockName(), item.rank(),
                        item.totalScore(), item.reason()))
                .toList();
        return new PredictionCandidateResult(fact.candidateBatchId(), fact.predictionBatchId(),
                fact.strategyVersion(), fact.targetTradeDate(), items, fact.availabilityReason());
    }

    /** 候选批次查询请求。 */
    public record PredictionCandidateRequest(String candidateBatchId) {
    }

    /** 候选批次脱敏结果。 */
    public record PredictionCandidateResult(String candidateBatchId, String predictionBatchId,
                                            String strategyVersion, LocalDate targetTradeDate,
                                            List<PredictionCandidateItem> items, String availabilityReason) {
    }

    /** 候选股票摘要。 */
    public record PredictionCandidateItem(String stockCode, String stockName, int rank,
                                          double totalScore, String reason) {
    }
}
// AI_GENERATE_END------------