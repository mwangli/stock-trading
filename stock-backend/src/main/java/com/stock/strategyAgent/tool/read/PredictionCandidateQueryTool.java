// AI_GENERATE_START ----
package com.stock.strategyAgent.tool.read;

import com.stock.strategyAgent.domain.entity.StockRanking;
import com.stock.strategyAgent.persistence.RankingRepository;
import com.stock.strategyAgent.tool.AgentToolSupport;
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

    private final RankingRepository rankingRepository;
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
        return toolSupport.execute("queryPredictionCandidate", () -> {
            List<StockRanking> rankings = rankingRepository.findByCandidateBatchIdOrderByRankAsc(
                    request.candidateBatchId().trim());
            if (rankings.isEmpty()) {
                return new PredictionCandidateResult(request.candidateBatchId().trim(), null, null, null, List.of());
            }
            StockRanking first = rankings.get(0);
            List<PredictionCandidateItem> items = rankings.stream().limit(20)
                    .map(item -> new PredictionCandidateItem(item.getStockCode(), item.getStockName(), item.getRank(),
                            item.getTotalScore(), item.getReason()))
                    .toList();
            return new PredictionCandidateResult(first.getCandidateBatchId(), first.getPredictionBatchId(),
                    first.getStrategyConfigVersion(), first.getTargetTradeDate(), items);
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
        return toolSupport.execute("queryLatestPredictionCandidate", () -> rankingRepository
                .findFirstByTargetTradeDateOrderByCalculateTimeDesc(targetTradeDate)
                .map(ranking -> query(new PredictionCandidateRequest(ranking.getCandidateBatchId())))
                .orElseGet(() -> new PredictionCandidateResult(null, null, null, targetTradeDate, List.of())));
    }

    /** 候选批次查询请求。 */
    public record PredictionCandidateRequest(String candidateBatchId) {
    }

    /** 候选批次脱敏结果。 */
    public record PredictionCandidateResult(String candidateBatchId, String predictionBatchId,
                                            String strategyVersion, LocalDate targetTradeDate,
                                            List<PredictionCandidateItem> items) {
    }

    /** 候选股票摘要。 */
    public record PredictionCandidateItem(String stockCode, String stockName, int rank,
                                          double totalScore, String reason) {
    }
}
// AI_GENERATE_END ----
