// AI_GENERATE_START ----
package com.stock.tradingExecutor.job.bootstrap;

import com.stock.tradingExecutor.job.JobConfig;
import com.stock.tradingExecutor.job.JobConfigRepository;
import com.stock.tradingExecutor.job.JobSchedulerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 日线 T+1 基准回测任务引导器。
 * 默认保持禁用，必须在行情数据审计后由人工启用。
 *
 * @author mwangli
 * @since 2026-09-25
 */
@Slf4j
@Component
@Order(110)
@RequiredArgsConstructor
public class BacktestJobBootstrap implements CommandLineRunner {

    private static final String JOB_NAME = "daily-five-year-baseline-backtest";

    private final JobConfigRepository jobConfigRepository;
    private final JobSchedulerService jobSchedulerService;

    /**
     * 初始化最近五年日线动量基准任务。
     *
     * @param args 应用启动参数
     */
    @Override
    public void run(String... args) {
        JobConfig config = jobConfigRepository.findByJobName(JOB_NAME)
                .orElseGet(() -> jobConfigRepository.save(new JobConfig()
                        .setJobName(JOB_NAME)
                        .setDescription("最近五年日线动量固定成交基准回测")
                        .setBeanName("dailyBaselineBacktestJob")
                        .setMethodName("runFiveYearMomentumBaseline")
                        .setCronExpression("0 0 5 ? * SUN")
                        .setStatus(0)));
        try {
            jobSchedulerService.registerJob(config);
            log.info("[BacktestJobBootstrap] 已校验并注册固定任务: {}", JOB_NAME);
        } catch (RuntimeException exception) {
            log.error("[BacktestJobBootstrap] 固定任务即时注册失败，主任务引导器将再次重试: jobName={}",
                    JOB_NAME, exception);
        }
    }
}
// AI_GENERATE_END ----
