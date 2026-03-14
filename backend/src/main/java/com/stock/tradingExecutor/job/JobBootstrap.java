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
 * 应用启动时初始化默认定时任务并启动调度器，在独立线程中执行不阻塞启动
 */
@Slf4j
@Component
public class JobBootstrap implements ApplicationRunner {

    private final JobConfigRepository jobConfigRepository;
    private final JobSchedulerService jobSchedulerService;
    private final Executor applicationTaskExecutor;

    public JobBootstrap(JobConfigRepository jobConfigRepository,
                        JobSchedulerService jobSchedulerService,
                        @Qualifier("applicationTaskExecutor") Executor applicationTaskExecutor) {
        this.jobConfigRepository = jobConfigRepository;
        this.jobSchedulerService = jobSchedulerService;
        this.applicationTaskExecutor = applicationTaskExecutor;
    }

    @Override
    public void run(ApplicationArguments args) {
        log.info("========== [任务初始化] 已提交后台执行，不阻塞启动 ==========");
        applicationTaskExecutor.execute(this::initJobsAsync);
    }

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
                    .setStatus(0));

            defaultJobs.add(new JobConfig()
                    .setJobName("stockSelection")
                    .setDescription("运行双因子模型选出次日优选股")
                    .setBeanName("strategyScheduler")
                    .setMethodName("runStockSelection")
                    .setCronExpression("0 0 17 * * MON-FRI")
                    .setStatus(1));

            defaultJobs.add(new JobConfig()
                    .setJobName("signalGeneration")
                    .setDescription("基于选股结果生成买入/卖出信号")
                    .setBeanName("strategyScheduler")
                    .setMethodName("runSignalGeneration")
                    .setCronExpression("0 30 17 * * MON-FRI")
                    .setStatus(1));

            defaultJobs.add(new JobConfig()
                    .setJobName("intradayRiskControl")
                    .setDescription("T+1 卖出检查、止损和止盈")
                    .setBeanName("strategyScheduler")
                    .setMethodName("checkIntradaySell")
                    .setCronExpression("0 * 9-15 * * MON-FRI")
                    .setStatus(1));

            defaultJobs.add(new JobConfig()
                    .setJobName("forceSellCheck")
                    .setDescription("收盘前强制卖出所有 T+1 持仓")
                    .setBeanName("strategyScheduler")
                    .setMethodName("checkForceSell")
                    .setCronExpression("0 57 14 * * MON-FRI")
                    .setStatus(1));

            defaultJobs.add(new JobConfig()
                    .setJobName("strategySwitcher")
                    .setDescription("检查市场状况以切换活跃策略")
                    .setBeanName("strategyScheduler")
                    .setMethodName("checkTimeBasedSwitch")
                    .setCronExpression("0 * 9-15 * * MON-FRI")
                    .setStatus(1));

            // 模型训练记录全量同步（每日凌晨 03:30）
            defaultJobs.add(new JobConfig()
                    .setJobName("modelTrainingRecordSync")
                    .setDescription("每日对齐股票基础表、LSTM 模型与训练记录表")
                    .setBeanName("modelTrainingRecordSyncJob")
                    .setMethodName("syncAllStocks")
                    .setCronExpression("0 30 3 * * ?")
                    .setStatus(1));

            for (JobConfig job : defaultJobs) {
                if (jobConfigRepository.findByJobName(job.getJobName()).isEmpty()) {
                    job.setCreateTime(LocalDateTime.now());
                    job.setUpdateTime(LocalDateTime.now());
                    jobConfigRepository.save(job);
                    log.info("[任务初始化] 创建默认任务: {}", job.getJobName());
                }
            }

            jobSchedulerService.startAllActiveJobs();
            log.info("========== [任务初始化] 完成 ==========");
        } catch (Exception e) {
            log.error("[任务初始化] 执行失败", e);
        }
    }
}
