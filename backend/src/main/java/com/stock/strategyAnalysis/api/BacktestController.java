// AI_GENERATE_START ------
package com.stock.strategyAnalysis.api;

import com.stock.dataCollector.domain.dto.ResponseDTO;
import com.stock.strategyAnalysis.domain.dto.BacktestDataAuditDto;
import com.stock.strategyAnalysis.domain.dto.BacktestResultDto;
import com.stock.strategyAnalysis.domain.dto.DailyBaselineBacktestRequestDto;
import com.stock.strategyAnalysis.service.BacktestDataAuditService;
import com.stock.strategyAnalysis.service.BacktestExecutionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * T+1 回测管理接口。
 * 当前仅开放数据审计，真实基准引擎通过门禁后再开放执行接口。
 *
 * @author mwangli
 * @since 2026-09-25
 */
@Slf4j
@RestController
@RequestMapping("/api/backtests")
@RequiredArgsConstructor
public class BacktestController {

    private final BacktestDataAuditService backtestDataAuditService;
    private final BacktestExecutionService backtestExecutionService;

    /**
     * 审计指定日期区间的回测数据可用性。
     *
     * @param startDate 开始日期，格式为 yyyy-MM-dd
     * @param endDate 结束日期，格式为 yyyy-MM-dd
     * @return 数据覆盖和门禁阻塞项
     */
    @GetMapping("/data-audit")
    public ResponseDTO<BacktestDataAuditDto> auditData(
            @RequestParam LocalDate startDate,
            @RequestParam LocalDate endDate) {
        log.info("[Backtest] 审计回测数据可用性: startDate={}, endDate={}", startDate, endDate);
        return ResponseDTO.success(backtestDataAuditService.audit(startDate, endDate));
    }

    /**
     * 执行明确允许的降级日线 T+1 固定成交基准。
     *
     * @param request 日期、股票池和候选选择规则
     * @return 可追溯的回测汇总与逐笔明细
     */
    @PostMapping("/daily-baseline")
    public ResponseDTO<BacktestResultDto> runDailyBaseline(
            @Valid @RequestBody DailyBaselineBacktestRequestDto request) {
        log.info("[Backtest] 执行日线降级基准: startDate={}, endDate={}, type={}, allowDegraded={}",
                request.getStartDate(), request.getEndDate(), request.getBaselineType(),
                request.isAllowDegradedDailyBaseline());
        return ResponseDTO.success(backtestExecutionService.runDailyBaseline(request));
    }

    /**
     * 查询已持久化的回测汇总和逐笔交易。
     *
     * @param resultId 回测结果标识
     * @return 完整回测结果
     */
    @GetMapping("/result/{resultId}")
    public ResponseDTO<BacktestResultDto> getResult(@PathVariable String resultId) {
        log.info("[Backtest] 查询回测结果: resultId={}", resultId);
        return ResponseDTO.success(backtestExecutionService.findResult(resultId));
    }
}
// AI_GENERATE_END ------
