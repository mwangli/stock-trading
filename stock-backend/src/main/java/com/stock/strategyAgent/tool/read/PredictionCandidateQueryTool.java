// AI_GENERATE_START -
package com.stock.strategyAgent.tool.read;

import com.stock.strategyAgent.strategy.domain.entity.StockRanking;
import com.stock.strategyAgent.strategy.persistence.RankingRepository;
import com.stock.strategyAgent.tool.ToolDefinition;
import com.stock.strategyAgent.tool.ToolGateway;
import com.stock.strategyAgent.tool.ToolPermission;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.InitializingBean;
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
public class PredictionCandidateQueryTool implements InitializingBean {

    private final ToolGateway toolGateway;
    private final RankingRepository rankingRepository;

    /**
     * 注册候选批次查询工具。
     */
    @Override
    public void afterPropertiesSet() {
        toolGateway.register(ToolDefinition.builder()
                        .name("queryPredictionCandidate")
                        .version("v1")
                        .description("查询指定候选批次的脱敏排名摘要")
                        .requiredPermission(ToolPermission.READ_ONLY)
                        .readOnly(true)
                        .maxResultSize(6000)
                        .timeoutMs(5000L)
                        .build(),
                (input, context) -> query(input));
    }

    private PredictionCandidateResult query(Object input) {
        if (!(input instanceof PredictionCandidateRequest request)
                || !StringUtils.hasText(request.candidateBatchId())) {
            throw new IllegalArgumentException("candidateBatchId 不能为空");
        }
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
// AI_GENERATE_END -
