// AI_GENERATE_START -----
package com.stock.tradingExecutor.job;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

/**
 * 应用启动时初始化默认定时任务并启动统一调度器。
 * 只注册数据同步、真实模型选股和必要的维护任务。
 *
 * @author mwangli
 * @since 2026-09-25
 */
@Slf4j
@Component
public class JobBootstrap implements ApplicationRunner {

    private final JobConfigRepository jobConfigRepository;
    private final JobSchedulerService jobSchedulerService;
    private final Executor applicationTaskExecutor;

    /**
     * 创建默认任务引导器。
     *
     * @param jobConfigRepository 任务配置仓库
     * @param jobSchedulerService 统一任务调度服务
     * @param applicationTaskExecutor 应用异步线程池
     */
    public JobBootstrap(JobConfigRepository jobConfigRepository,
                        JobSchedulerService jobSchedulerService,
                        @Qualifier("applicationTaskExecutor") Executor applicationTaskExecutor) {
        this.jobConfigRepository = jobConfigRepository;
        this.jobSchedulerService = jobSchedulerService;
        this.applicationTaskExecutor = applicationTaskExecutor;
    }

    /**
     * 提交默认任务初始化流程到应用线程池。
     *
     * @param args 应用启动参数
     */
    @Override
    public void run(ApplicationArguments args) {
        log.info("========== [任务初始化] 已提交后台执行，不阻塞启动 ==========");
        applicationTaskExecutor.execute(this::initJobsAsync);
    }

    /**
     * 创建缺失的默认任务并启动当前数据库中明确启用的任务。
     *
     * 固定任务由代码维护，不提供通用动态任务管理入口。
     */
    @Transactional
    protected void initJobsAsync() {
        try {
            log.info("========== [任务初始化] 检查并初始化默认定时任务 ==========");
            List<JobConfig> defaultJobs = new ArrayList<>();

            defaultJobs.add(new JobConfig()
                    .setJobName("stockListSync")
                    .setDescription("每周同步证券 API 股票代码和名称")
                    .setBeanName("dataSyncScheduler")
                    .setMethodName("syncStockListDaily")
                    .setCronExpression("0 0 1 * * SUN")
                    .setStatus(1));

            defaultJobs.add(new JobConfig()
                    .setJobName("dailyStockDataSync")
                    .setDescription("每日收盘后同步所有股票日 K 线数据")
                    .setBeanName("dataSyncScheduler")
                    .setMethodName("syncDailyStockData")
                    .setCronExpression("0 0 18 * * MON-FRI")
                    .setStatus(1));

            defaultJobs.add(new JobConfig()
                    .setJobName("historicalDataSync")
                    .setDescription("深度同步所有股票历史数据 (周维护)")
                    .setBeanName("dataSyncScheduler")
                    .setMethodName("syncAllHistoricalData")
                    .setCronExpression("0 0 2 * * SUN")
                    .setStatus(1));

            defaultJobs.add(new JobConfig()
                    .setJobName("newsFullSync")
                    .setDescription("全量新闻采集：每周日 6:00 全股票最新 100 条新闻/公告")
                    .setBeanName("dataSyncScheduler")
                    .setMethodName("collectNewsFullSync")
                    .setCronExpression("0 0 6 * * SUN")
                    .setStatus(0));

            defaultJobs.add(new JobConfig()
                    .setJobName("newsDailySync")
                    .setDescription("每日增量新闻采集：每交易日 6:00 全股票最新 100 条新闻/公告")
                    .setBeanName("dataSyncScheduler")
                    .setMethodName("collectNewsDailySync")
                    .setCronExpression("0 0 6 * * MON-FRI")
                    .setStatus(1));

            defaultJobs.add(new JobConfig()
                    .setJobName("lstmOfflineTraining")
                    .setDescription("收盘后更新缺失或过期的 LSTM 股票模型")
                    .setBeanName("offlineLstmTrainingJob")
                    .setMethodName("trainStaleModels")
                    .setCronExpression("0 0 19 * * MON-FRI")
                    .setStatus(1));

            defaultJobs.add(new JobConfig()
                    .setJobName("stockSelection")
                    .setDescription("使用已训练 LSTM 与近期新闻情感生成真实交易候选")
                    .setBeanName("strategyScheduler")
                    .setMethodName("runStockSelection")
                    .setCronExpression("0 30 6 * * MON-FRI")
                    .setStatus(1));

            defaultJobs.add(new JobConfig()
                    .setJobName("realTradingAutoBuy")
                    .setDescription("无人值守模式执行当日真实模型候选买入")
                    .setBeanName("realTradingJob")
                    .setMethodName("executeAutoBuys")
                    .setCronExpression("0 35 9 * * MON-FRI")
                    .setStatus(1));

            defaultJobs.add(new JobConfig()
                    .setJobName("realTradingT1Exit")
                    .setDescription("无人值守模式检查真实持仓止损、止盈和尾盘退出")
                    .setBeanName("realTradingJob")
                    .setMethodName("checkT1Exits")
                    .setCronExpression("0 50 14 * * MON-FRI")
                    .setStatus(1));

            jobConfigRepository.deleteByJobName("modelTrainingRecordSync");

            for (JobConfig job : defaultJobs) {
                JobConfig fixedJob = jobConfigRepository.findByJobName(job.getJobName())
                        .orElseGet(() -> job.setCreateTime(LocalDateTime.now()));
                fixedJob.setDescription(job.getDescription());
                fixedJob.setBeanName(job.getBeanName());
                fixedJob.setMethodName(job.getMethodName());
                fixedJob.setCronExpression(job.getCronExpression());
                fixedJob.setStatus(job.getStatus());
                fixedJob.setUpdateTime(LocalDateTime.now());
                jobConfigRepository.save(fixedJob);
                log.info("[任务初始化] 应用固定任务配置: {}", fixedJob.getJobName());
            }

            jobSchedulerService.startAllActiveJobs();
            log.info("========== [任务初始化] 完成 ==========");
        } catch (Exception e) {
            log.error("[任务初始化] 执行失败", e);
        }
    }
}
// AI_GENERATE_END -----
