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
 * 券商只读同步任务引导器。
 * 默认任务均为禁用状态，必须在登录、数据库和脱敏样本门禁通过后人工启用。
 *
 * @author mwangli
 * @since 2026-09-25
 */
@Slf4j
@Component
@Order(100)
@RequiredArgsConstructor
public class BrokerSyncJobBootstrap implements CommandLineRunner {

    private final JobConfigRepository jobConfigRepository;

    /**
     * 初始化券商当前事实和五年历史同步任务。
     *
     * @param args 应用启动参数
     */
    @Override
    public void run(String... args) {
        createIfAbsent("broker-current-facts-sync",
                "券商账户、持仓、当日委托和成交只读同步",
                "syncCurrentFacts", "0 0/5 9-15 * * MON-FRI");
        createIfAbsent("broker-five-year-history-sync",
                "最近五年券商历史委托和成交按月回补",
                "syncLastFiveYears", "0 0 4 * * SUN");
    }

    private void createIfAbsent(String jobName, String description, String methodName, String cronExpression) {
        if (jobConfigRepository.findByJobName(jobName).isPresent()) {
            return;
        }
        JobConfig config = new JobConfig()
                .setJobName(jobName)
                .setDescription(description)
                .setBeanName("brokerDataSyncJob")
                .setMethodName(methodName)
                .setCronExpression(cronExpression)
                .setStatus(0);
        jobConfigRepository.save(config);
        log.info("[BrokerSyncJobBootstrap] 已创建默认禁用任务: {}", jobName);
    }
}
// AI_GENERATE_END --
