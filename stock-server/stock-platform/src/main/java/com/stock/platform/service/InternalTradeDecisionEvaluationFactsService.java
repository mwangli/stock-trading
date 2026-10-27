// AI_GENERATE_START------
package com.stock.platform.service;

import com.stock.platform.dto.InternalTradeDecisionEvaluationFactRequest;
import com.stock.platform.dto.InternalTradeDecisionEvaluationFactResponse;
import com.stock.platform.dto.InternalTradeDecisionEvaluationFactsResponse;
import com.stock.data.dto.StockPriceItemDto;
import com.stock.data.service.MarketDataQueryService;
import com.stock.trading.dto.AutomaticTradeExecutionSummaryDto;
import com.stock.trading.service.AutomaticTradeExecutionBatchService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 批量提供 Agent 交易建议评估所需的行情和执行批次事实。
 *
 * @author mwangli
 * @since 2026-10-23
 */
@Service
@RequiredArgsConstructor
public class InternalTradeDecisionEvaluationFactsService {

    private static final int MAX_ITEMS = 500;

    private final MarketDataQueryService marketDataQueryService;
    private final AutomaticTradeExecutionBatchService executionBatchService;

    /**
     * 批量查询交易建议评估事实。
     *
     * @param requests 待查询项目
     * @return 最新行情日期和逐候选事实
     */
    public InternalTradeDecisionEvaluationFactsResponse query(
            List<InternalTradeDecisionEvaluationFactRequest> requests) {
        List<InternalTradeDecisionEvaluationFactRequest> safeRequests = requests == null ? List.of() : requests;
        if (safeRequests.size() > MAX_ITEMS) {
            throw new IllegalArgumentException("单次评估事实查询不能超过 500 条");
        }
        LocalDate latestMarketDate = marketDataQueryService.findLatestMarketDate();
        Map<String, AutomaticTradeExecutionSummaryDto> batches = new HashMap<>();
        List<InternalTradeDecisionEvaluationFactResponse> items = safeRequests.stream()
                .map(request -> toResponse(request, batches))
                .toList();
        return new InternalTradeDecisionEvaluationFactsResponse(latestMarketDate, items);
    }

    private InternalTradeDecisionEvaluationFactResponse toResponse(
            InternalTradeDecisionEvaluationFactRequest request,
            Map<String, AutomaticTradeExecutionSummaryDto> batches) {
        StockPriceItemDto price = marketDataQueryService.findPrice(
                request.stockCode(), request.targetTradeDate()).orElse(null);
        AutomaticTradeExecutionSummaryDto batch = batches.computeIfAbsent(
                request.candidateBatchId(), executionBatchService::findLatestByCandidateBatchId);
        return new InternalTradeDecisionEvaluationFactResponse(
                request.candidateBatchId(), request.stockCode(), request.targetTradeDate(),
                price == null ? null : price.getOpenPrice(),
                price == null ? null : price.getHighPrice(),
                price == null ? null : price.getLowPrice(),
                price == null ? null : price.getClosePrice(),
                batch == null ? null : batch.getBatchId(),
                batch == null ? null : batch.getStatus(),
                batch == null ? null : batch.getProcessedCount(),
                batch == null ? null : batch.getSuccessCount());
    }
}
// AI_GENERATE_END------
