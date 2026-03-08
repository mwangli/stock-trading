package com.stock.job.bootstrap;

import com.stock.job.entity.JobConfig;
import com.stock.job.repository.JobConfigRepository;
import com.stock.job.service.JobSchedulerService;
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
 * 应用启动时初始化默认定时任务并启动调度器
 * 在独立线程中执行，不阻塞应用启动
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
        
        // 1. 股票列表同步 (每周日凌晨1点)
        defaultJobs.add(new JobConfig()
            .setJobName("stockListSync")
            .setDescription("每周同步证券 API 股票代码和名称")
            .setBeanName("dataSyncScheduler")
            .setMethodName("syncStockListDaily")
            .setCronExpression("0 0 1 * * SUN")
            .setStatus(1));
            
        // 2. 每日数据同步 (工作日18点)
        defaultJobs.add(new JobConfig()
            .setJobName("dailyStockDataSync")
            .setDescription("每日收盘后同步所有股票日 K 线数据")
            .setBeanName("dataSyncScheduler")
            .setMethodName("syncDailyStockData")
            .setCronExpression("0 0 18 * * MON-FRI")
            .setStatus(1));
            
        // 3. 历史数据全量同步 (每周日凌晨2点)
        defaultJobs.add(new JobConfig()
            .setJobName("historicalDataSync")
            .setDescription("深度同步所有股票历史数据 (周维护)")
            .setBeanName("dataSyncScheduler")
            .setMethodName("syncAllHistoricalData")
            .setCronExpression("0 0 2 * * SUN")
            .setStatus(1));

        // 4. 新闻爬虫 (每小时)
        defaultJobs.add(new JobConfig()
            .setJobName("newsCrawler")
            .setDescription("每小时抓取财经新闻进行情感分析")
            .setBeanName("dataSyncScheduler")
            .setMethodName("collectHourlyNews")
            .setCronExpression("0 0 * * * *")
            .setStatus(0)); // 默认暂停
            
        // 5. 选股策略 (工作日17点)
        defaultJobs.add(new JobConfig()
            .setJobName("stockSelection")
            .setDescription("运行双因子模型选出次日优选股")
            .setBeanName("strategyScheduler")
            .setMethodName("runStockSelection")
            .setCronExpression("0 0 17 * * MON-FRI")
            .setStatus(1));

        // 6. 信号生成 (工作日17:30)
        defaultJobs.add(new JobConfig()
            .setJobName("signalGeneration")
            .setDescription("基于选股结果生成买入/卖出信号")
            .setBeanName("strategyScheduler")
            .setMethodName("runSignalGeneration")
            .setCronExpression("0 30 17 * * MON-FRI")
            .setStatus(1));
            
        // 7. 盘中风控 (工作日9-15点每分钟)
        defaultJobs.add(new JobConfig()
            .setJobName("intradayRiskControl")
            .setDescription("T+1 卖出检查、止损和止盈")
            .setBeanName("strategyScheduler")
            .setMethodName("checkIntradaySell")
            .setCronExpression("0 * 9-15 * * MON-FRI")
            .setStatus(1));

        // 8. 尾盘强制卖出 (工作日14:57)
        defaultJobs.add(new JobConfig()
            .setJobName("forceSellCheck")
            .setDescription("收盘前强制卖出所有 T+1 持仓")
            .setBeanName("strategyScheduler")
            .setMethodName("checkForceSell")
            .setCronExpression("0 57 14 * * MON-FRI")
            .setStatus(1));

        // 9. 策略切换 (工作日9-15点每分钟)
        defaultJobs.add(new JobConfig()
            .setJobName("strategySwitcher")
            .setDescription("检查市场状况以切换活跃策略")
            .setBeanName("strategyScheduler")
            .setMethodName("checkTimeBasedSwitch")
            .setCronExpression("0 * 9-15 * * MON-FRI")
            .setStatus(1));
            
        for (JobConfig job : defaultJobs) {
            if (jobConfigRepository.findByJobName(job.getJobName()).isEmpty()) {
                job.setCreateTime(LocalDateTime.now());
                job.setUpdateTime(LocalDateTime.now());
                jobConfigRepository.save(job);
                log.info("[任务初始化] 创建默认任务: {}", job.getJobName());
            }
        }
        
        // 启动所有任务
        jobSchedulerService.startAllActiveJobs();
        log.info("========== [任务初始化] 完成 ==========");
        } catch (Exception e) {
            log.error("[任务初始化] 执行失败", e);
        }
    }
}
