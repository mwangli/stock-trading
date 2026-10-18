// AI_GENERATE_START- -
package com.stock.tradingExecutor.service;

import com.stock.strategyAgent.application.JobExecutionQuery;
import com.stock.strategyAgent.application.JobExecutionSummary;
import com.stock.tradingExecutor.JobExecutionRecord;
import com.stock.tradingExecutor.JobExecutionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * 交易执行模块提供的任务运行查询适配器。
 * 将调度模块内部实体转换为 strategyAgent 可消费的脱敏摘要。
 *
 * @author mwangli
 * @since 2026-10-17
 */
@Service
@RequiredArgsConstructor
public class JobExecutionQueryAdapter implements JobExecutionQuery {

    private final JobExecutionService jobExecutionService;

    /**
     * 查询指定任务最近一次运行摘要。
     *
     * @param jobName 固定任务名称
     * @return 最近一次运行摘要，没有记录时返回空
     */
    @Override
    public Optional<JobExecutionSummary> getLatestRun(String jobName) {
        JobExecutionRecord record = jobExecutionService.getLatestRun(jobName);
        return Optional.ofNullable(record)
                .map(item -> new JobExecutionSummary(
                        item.getStatus() == null ? null : item.getStatus().name(),
                        item.getErrorMessage()));
    }
}
// AI_GENERATE_END -
