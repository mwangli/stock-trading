// AI_GENERATE_START -------
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
 * 券商只读同步任务引导器。
 * 当前事实同步默认启用，供账户总览读取数据库快照；历史回补默认禁用。
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
    private final JobSchedulerService jobSchedulerService;

    /**
     * 初始化券商当前事实和五年历史同步任务。
     *
     * @param args 应用启动参数
     */
    @Override
    public void run(String... args) {
        applyFixedConfig("broker-current-facts-sync", "brokerDataSyncJob",
                "券商账户、持仓、当日委托和成交只读同步",
                "syncCurrentFacts", "0 0/5 9-15 ? * MON-FRI", 1);
        applyFixedConfig("broker-five-year-history-sync", "brokerDataSyncJob",
                "最近五年券商历史委托和成交按月回补",
                "syncLastFiveYears", "0 0 4 ? * SUN", 0);
        applyFixedConfig("daily-trade-performance-snapshot", "performanceReportJob",
                "交易日收盘后根据交易记录刷新收益统计",
                "refreshDailySnapshot", "0 0 16 ? * MON-FRI", 1);
    }

    private void applyFixedConfig(String jobName, String beanName, String description, String methodName,
                                  String cronExpression, int status) {
        JobConfig config = jobConfigRepository.findByJobName(jobName)
                .orElseGet(() -> new JobConfig().setJobName(jobName));
        config.setDescription(description)
                .setBeanName(beanName)
                .setMethodName(methodName)
                .setCronExpression(cronExpression)
                .setStatus(status);
        JobConfig savedConfig = jobConfigRepository.save(config);
        registerFixedJob(savedConfig);
        log.info("[BrokerSyncJobBootstrap] 已应用固定任务配置: jobName={}, enabled={}", jobName, status == 1);
    }

    private void registerFixedJob(JobConfig config) {
        try {
            jobSchedulerService.registerJob(config);
        } catch (RuntimeException exception) {
            log.error("[BrokerSyncJobBootstrap] 固定任务即时注册失败，主任务引导器将再次重试: jobName={}",
                    config.getJobName(), exception);
        }
    }
}
// AI_GENERATE_END -------
