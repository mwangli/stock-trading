// AI_GENERATE_START -----------
package com.stock.tradingExecutor.job;

import com.stock.systemConfig.service.SystemRuntimeConfigService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.concurrent.Executor;

/**
 * 应用启动时协调数据库任务配置与 Quartz 持久化调度状态。
 * 固定任务的默认数据由 Flyway 维护，此处只执行幂等注册和本节点开关同步。
 *
 * @author mwangli
 * @since 2026-09-25
 */
@Slf4j
@Component
public class JobBootstrap implements ApplicationRunner {

    private final JobSchedulerService jobSchedulerService;
    private final Executor applicationTaskExecutor;
    private final SystemRuntimeConfigService runtimeConfigService;

    /**
     * 创建默认任务引导器。
     *
     * @param jobSchedulerService 统一任务调度服务
     * @param applicationTaskExecutor 应用异步线程池
     * @param runtimeConfigService 系统运行控制配置服务
     */
    public JobBootstrap(JobSchedulerService jobSchedulerService,
                        @Qualifier("applicationTaskExecutor") Executor applicationTaskExecutor,
                        SystemRuntimeConfigService runtimeConfigService) {
        this.jobSchedulerService = jobSchedulerService;
        this.applicationTaskExecutor = applicationTaskExecutor;
        this.runtimeConfigService = runtimeConfigService;
    }

    /**
     * 提交默认任务初始化流程到应用线程池。
     *
     * @param args 应用启动参数
     */
    @Override
    public void run(ApplicationArguments args) {
        log.info("========== [任务协调] 已提交后台执行，不阻塞启动 ==========");
        applicationTaskExecutor.execute(this::reconcileJobs);
    }

    private void reconcileJobs() {
        try {
            log.info("========== [任务协调] 开始同步数据库配置与 Quartz ==========");
            jobSchedulerService.reconcileAllJobs();
            runtimeConfigService.synchronizeSchedulerState();
            log.info("========== [任务协调] 完成 ==========");
        } catch (Exception exception) {
            log.error("[任务协调] 执行失败", exception);
        }
    }
}
// AI_GENERATE_END -----------
