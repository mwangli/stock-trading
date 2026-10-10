// AI_GENERATE_START --
package com.stock.strategyAnalysis.service;

import com.stock.strategyAnalysis.domain.dto.BacktestResultDto;
import com.stock.strategyAnalysis.domain.dto.DailyBaselineBacktestRequestDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 回测执行编排服务。
 * 负责执行日线基准并在返回前持久化汇总和逐笔交易。
 *
 * @author mwangli
 * @since 2026-09-25
 */
@Service
@RequiredArgsConstructor
public class BacktestExecutionService {

    private final DailyBaselineBacktestService dailyBaselineBacktestService;
    private final BacktestPersistenceService backtestPersistenceService;

    /**
     * 执行并保存日线 T+1 基准。
     *
     * @param request 回测请求
     * @return 已持久化的回测结果
     */
    public BacktestResultDto runDailyBaseline(DailyBaselineBacktestRequestDto request) {
        BacktestResultDto result = dailyBaselineBacktestService.run(request);
        backtestPersistenceService.saveCompleted(request, result);
        return result;
    }

    /**
     * 查询已完成的回测结果。
     *
     * @param resultId 回测结果标识
     * @return 回测汇总和逐笔明细
     */
    public BacktestResultDto findResult(String resultId) {
        return backtestPersistenceService.findResult(resultId);
    }
}
// AI_GENERATE_END --
