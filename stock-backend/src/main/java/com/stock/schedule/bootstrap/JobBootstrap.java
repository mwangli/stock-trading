// AI_GENERATE_START---------------------------
package com.stock.schedule.bootstrap;

import com.stock.market.dto.StockListSyncResult;
import com.stock.market.service.StockListSyncService;
import com.stock.market.service.StockQueryService;
import com.stock.schedule.entity.JobConfig;
import com.stock.schedule.repository.JobConfigRepository;
import com.stock.schedule.service.JobSchedulerService;

import com.stock.execution.service.SystemRuntimeConfigService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.Executor;

/**
 * 应用启动时补齐 MySQL 固定任务配置，并据此重建 Quartz 内存调度状态。
 * 已存在任务保留用户维护的 Cron 和启停状态，仅为全新数据库写入默认值。
 *
 * @author mwangli
 * @since 2026-09-25
 */
@Slf4j
@Component
public class JobBootstrap implements ApplicationRunner {

    private static final List<JobConfig> DEFAULT_JOBS = List.of(
            defaultJob("stockListSync", "0 0 1 ? * SUN", "每周同步证券 API 股票代码和名称", "data", "FULL"),
            defaultJob("dailyStockDataSync", "0 0 18 ? * MON-FRI", "每日仅同步当前已确认交易日的全市场日线增量", "data", "INCREMENTAL"),
            defaultJob("stockPriceHistoryBackfill", "0 0 3 ? * SAT", "每周回补最近三年已确认交易日的股票历史日线缺口", "data", "BACKFILL"),
            defaultJob("newsDailySync", "0 0 5 ? * MON-FRI", "每日同步股票新闻和公告", "data", "INCREMENTAL"),
            defaultPausedJob("newsCollectionBackfill", "0 0 2 ? * *", "分批回填历史新闻采集治理和事件事实", "data", "BACKFILL"),
            defaultPausedJob("newsFeatureSnapshotBackfill", "0 30 2 ? * *",
                    "分批生成 temporal-news-v1 股票日级快照并执行点时审计", "model", "BACKFILL"),
            defaultPausedJob("sentimentMengziShadow", "0 0 1 ? * MON-FRI",
                    "顺序执行 Mengzi 情感影子推理和自动路由评估", "model", "INCREMENTAL"),
            defaultJob("tradeCalendarMonthlySync", "0 10 4 1 * ?", "每月回补交易日历缺口并维持未来一年滚动窗口", "data", "BACKFILL"),
            defaultJob("lstmOfflineTraining", "0 0 19 ? * MON-FRI", "收盘后检查并更新缺失或过期的共享 LSTM 模型", "model", "INCREMENTAL"),
            defaultJob("dailyModelPrediction", "0 15 6 ? * MON-FRI", "工作日开盘前使用最近完整日线生成全市场预测", "model", "INCREMENTAL"),
            defaultJob("masterShadowPrediction", "0 20 6 ? * MON-FRI",
                    "对生产预测批次执行 MASTER 影子预测和动态权重评估", "model", "INCREMENTAL"),
            defaultJob("globalDataCleanup", "0 30 3 1 * ?",
                    "每月清理超过保留期且不再引用的业务历史数据", "platform", "CLEANUP"),
            defaultJob("stockSelection", "0 30 6 ? * MON-FRI", "使用已训练模型与近期新闻情感生成交易候选", "strategy", "FULL"),
            defaultJob("realTradingAutoBuy", "0 35 9 ? * MON-FRI", "执行真实模型候选买入检查", "trading", "INCREMENTAL"),
            defaultJob("realTradingT1Exit", "0 50 14 ? * MON-FRI", "检查真实持仓止损、止盈和 T+1 退出", "trading", "INCREMENTAL"),
            defaultJob("broker-current-facts-sync", "0 0/5 9-15 ? * MON-FRI", "同步券商账户、持仓、委托和成交事实", "trading", "INCREMENTAL"));

    private final JobSchedulerService jobSchedulerService;
    private final JobConfigRepository jobConfigRepository;
    private final Executor applicationTaskExecutor;
    private final SystemRuntimeConfigService runtimeConfigService;
    private final StockQueryService stockQueryService;
    private final StockListSyncService stockListSyncService;

    /**
     * 创建默认任务引导器。
     *
     * @param jobSchedulerService 统一任务调度服务
     * @param jobConfigRepository MySQL 任务配置仓储
     * @param applicationTaskExecutor 应用异步线程池
     * @param runtimeConfigService 系统运行控制配置服务
     * @param stockQueryService 股票基础信息查询服务
     * @param stockListSyncService 股票列表同步服务
     */
    public JobBootstrap(JobSchedulerService jobSchedulerService,
                        JobConfigRepository jobConfigRepository,
                        @Qualifier("applicationTaskExecutor") Executor applicationTaskExecutor,
                        SystemRuntimeConfigService runtimeConfigService,
                        StockQueryService stockQueryService,
                        StockListSyncService stockListSyncService) {
        this.jobSchedulerService = jobSchedulerService;
        this.jobConfigRepository = jobConfigRepository;
        this.applicationTaskExecutor = applicationTaskExecutor;
        this.runtimeConfigService = runtimeConfigService;
        this.stockQueryService = stockQueryService;
        this.stockListSyncService = stockListSyncService;
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
            log.info("========== [任务协调] 开始补齐 MySQL 配置并重建 Quartz 内存任务 ==========");
            initializeDefaultJobs();
            jobSchedulerService.reconcileAllJobs();
            runtimeConfigService.synchronizeSchedulerState();
            initializeStockDataIfNeeded();
            log.info("========== [任务协调] 完成 ==========");
        } catch (Exception exception) {
            log.error("[任务协调] 执行失败", exception);
        }
    }

    private void initializeDefaultJobs() {
        int created = 0;
        for (JobConfig defaultJob : DEFAULT_JOBS) {
            if (jobConfigRepository.findByJobName(defaultJob.getJobName()).isPresent()) {
                continue;
            }
            jobConfigRepository.save(defaultJob);
            created++;
        }
        log.info("[任务协调] MySQL 固定任务配置补齐完成: created={}, total={}", created, DEFAULT_JOBS.size());
    }

    private void initializeStockDataIfNeeded() {
        if (!runtimeConfigService.isStartupDataSyncEnabled()) {
            log.info("[任务协调] 启动数据初始化已关闭，跳过股票列表检查");
            return;
        }
        long stockCount = stockQueryService.countStocks();
        if (stockCount >= 4000) {
            log.info("[任务协调] 股票基础数据充足，跳过启动同步: count={}", stockCount);
            return;
        }
        log.warn("[任务协调] 股票基础数据不足，开始启动同步: count={}", stockCount);
        StockListSyncResult result = stockListSyncService.syncStockList();
        log.info("[任务协调] 启动股票列表同步完成: total={}, saved={}, updated={}, cleaned={}, ignored={}, failed={}, costMs={}",
                result.getTotalCount(), result.getSavedCount(), result.getUpdatedCount(), result.getCleanedCount(),
                result.getIgnoredMissingCount(), result.getFailedCount(), result.getCostTimeMs());
    }

    private static JobConfig defaultJob(String jobName, String cronExpression, String description,
                                        String module, String syncMode) {
        return new JobConfig()
                .setJobName(jobName)
                .setCronExpression(cronExpression)
                .setStatus(1)
                .setDescription(description)
                .setModule(module)
                .setSyncMode(syncMode);
    }

    private static JobConfig defaultPausedJob(String jobName, String cronExpression, String description,
                                              String module, String syncMode) {
        return defaultJob(jobName, cronExpression, description, module, syncMode).setStatus(0);
    }
}
// AI_GENERATE_END---------------------------
