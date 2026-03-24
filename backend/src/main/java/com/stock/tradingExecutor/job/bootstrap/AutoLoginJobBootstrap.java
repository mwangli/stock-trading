package com.stock.tradingExecutor.job.bootstrap;

import com.stock.tradingExecutor.job.JobConfig;
import com.stock.tradingExecutor.job.JobConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * 自动登录维护任务引导器
 * 在应用启动时初始化自动登录维护任务配置
 *
 * @author mwangli
 * @since 2026-03-22
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AutoLoginJobBootstrap implements CommandLineRunner {

    private final JobConfigRepository jobConfigRepository;

    @Override
    public void run(String... args) {
        initAutoLoginMaintenanceJob();
    }

    private void initAutoLoginMaintenanceJob() {
        String jobName = "auto-login-maintain";
        
        Optional<JobConfig> existing = jobConfigRepository.findByJobName(jobName);
        if (existing.isPresent()) {
            log.info("[AutoLoginJobBootstrap] 自动登录维护任务已存在");
            return;
        }

        JobConfig config = new JobConfig()
                .setJobName(jobName)
                .setCronExpression("0 0/30 * * * ?") // 每30分钟执行一次
                .setStatus(1) // 1=启用, 0=禁用
                .setDescription("中信证券自动登录状态维护任务")
                .setBeanName("autoLoginMaintenanceJob")
                .setMethodName("maintainLoginStatus")
                .setParams(null);

        jobConfigRepository.save(config);
        log.info("[AutoLoginJobBootstrap] 自动登录维护任务配置已创建");
    }
}