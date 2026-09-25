// AI_GENERATE_START --
package com.stock.tradingExecutor.job.bootstrap;

import com.stock.tradingExecutor.job.JobConfig;
import com.stock.tradingExecutor.job.JobConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 策略熔断恢复检查任务引导器。
 * 在统一调度器启动前注册每分钟恢复检查任务，避免业务类自行声明定时调度。
 *
 * @author mwangli
 * @since 2026-09-25
 */
@Slf4j
@Component
@Order(90)
@RequiredArgsConstructor
public class CircuitBreakerJobBootstrap implements CommandLineRunner {

    private static final String JOB_NAME = "strategy-circuit-breaker-recovery";

    private final JobConfigRepository jobConfigRepository;

    /**
     * 初始化策略熔断恢复检查任务。
     *
     * @param args 应用启动参数
     */
    @Override
    public void run(String... args) {
        if (jobConfigRepository.findByJobName(JOB_NAME).isPresent()) {
            return;
        }

        JobConfig config = new JobConfig()
                .setJobName(JOB_NAME)
                .setDescription("每分钟检查策略熔断是否达到恢复时间")
                .setBeanName("circuitBreaker")
                .setMethodName("checkRecovery")
                .setCronExpression("0 * * * * ?")
                .setStatus(1);
        jobConfigRepository.save(config);
        log.info("[CircuitBreakerJobBootstrap] 已创建熔断恢复检查任务: {}", JOB_NAME);
    }
}
// AI_GENERATE_END --
