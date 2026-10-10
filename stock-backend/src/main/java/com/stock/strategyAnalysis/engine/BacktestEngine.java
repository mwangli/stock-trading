// AI_GENERATE_START --
package com.stock.strategyAnalysis.engine;

import com.stock.strategyAnalysis.domain.dto.BacktestResultDto;
import com.stock.strategyAnalysis.domain.entity.StrategyConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

/**
 * 策略回测引擎入口。
 * 在真实数据契约和基准执行器完成前拒绝返回占位业绩，防止伪结果进入参数优化或业务页面。
 *
 * @author mwangli
 * @since 2026-09-25
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BacktestEngine {

    /**
     * 执行真实回测。
     * 当前阶段尚未完成基准成交模拟，因此明确拒绝执行，不再返回固定收益率。
     *
     * @param config 策略配置
     * @param startDate 开始日期
     * @param endDate 结束日期
     * @return 回测结果
     */
    public BacktestResultDto runBacktest(StrategyConfig config, LocalDate startDate, LocalDate endDate) {
        String mode = config == null || config.getMode() == null ? "UNKNOWN" : config.getMode().name();
        log.warn("[Backtest] 拒绝执行未完成的真实回测: start={}, end={}, mode={}", startDate, endDate, mode);
        throw new IllegalStateException(
                "真实 T+1 回测引擎尚未通过数据和成交规则门禁，请先调用 /api/backtests/data-audit 检查数据");
    }

    /**
     * 对比多个策略配置。
     * 任一配置未通过真实回测门禁时终止比较，禁止使用占位指标选出所谓最优参数。
     *
     * @param configs 待比较的策略配置
     * @param startDate 开始日期
     * @param endDate 结束日期
     * @return 每个配置的真实回测结果
     */
    public List<BacktestResultDto> compareConfigs(List<StrategyConfig> configs, LocalDate startDate, LocalDate endDate) {
        return configs.stream()
                .map(config -> runBacktest(config, startDate, endDate))
                .toList();
    }
}
// AI_GENERATE_END --
