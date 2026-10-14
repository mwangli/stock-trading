// AI_GENERATE_START ----------
package com.stock.tradingExecutor.job;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.quartz.CronScheduleBuilder;
import org.quartz.Job;
import org.quartz.JobBuilder;
import org.quartz.JobDataMap;
import org.quartz.JobDetail;
import org.quartz.JobKey;
import org.quartz.ObjectAlreadyExistsException;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.quartz.TriggerKey;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * Quartz 固定任务调度服务。
 * 通过代码维护任务名称到 Quartz Job 类的明确映射，不使用反射调用业务方法。
 *
 * @author mwangli
 * @since 2026-10-11
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JobSchedulerService {

    private static final String QUARTZ_GROUP = "stock-trading-jobs";
    private static final Map<String, Class<? extends Job>> JOB_CLASSES = Map.ofEntries(
            Map.entry("stockListSync", StockListSyncQuartzJob.class),
            Map.entry("dailyStockDataSync", DailyStockDataSyncQuartzJob.class),
            Map.entry("historicalDataSync", HistoricalDataSyncQuartzJob.class),
            Map.entry("newsFullSync", NewsFullSyncQuartzJob.class),
            Map.entry("newsDailySync", NewsDailySyncQuartzJob.class),
            Map.entry("tradeCalendarMonthlySync", TradeCalendarMonthlySyncQuartzJob.class),
            Map.entry("lstmOfflineTraining", LstmOfflineTrainingQuartzJob.class),
            Map.entry("dailyModelPrediction", DailyModelPredictionQuartzJob.class),
            Map.entry("modelPredictionEvaluation", ModelPredictionEvaluationQuartzJob.class),
            Map.entry("stockSelection", StockSelectionQuartzJob.class),
            Map.entry("realTradingAutoBuy", RealTradingAutoBuyQuartzJob.class),
            Map.entry("realTradingT1Exit", RealTradingT1ExitQuartzJob.class),
            Map.entry("broker-current-facts-sync", BrokerCurrentFactsSyncQuartzJob.class),
            Map.entry("broker-five-year-history-sync", BrokerFiveYearHistorySyncQuartzJob.class),
            Map.entry("daily-five-year-baseline-backtest", DailyBaselineBacktestQuartzJob.class),
            Map.entry("strategy-auto-switch", StrategyAutoSwitchQuartzJob.class),
            Map.entry("strategy-evaluation", StrategyEvaluationQuartzJob.class),
            Map.entry("sentiment-model-evaluation", SentimentEvaluationQuartzJob.class),
            Map.entry("daily-trade-performance-snapshot", PerformanceReportQuartzJob.class));

    private final JobConfigRepository jobConfigRepository;
    private final Scheduler quartzScheduler;

    /**
     * 将数据库中的固定任务配置幂等同步到 Quartz 持久化存储。
     */
    public void reconcileAllJobs() {
        log.info("========== [任务调度] 开始协调 Quartz 持久化任务 ==========");
        List<JobConfig> jobs = jobConfigRepository.findAll();
        for (JobConfig job : jobs) {
            if (!JOB_CLASSES.containsKey(job.getJobName())) {
                log.warn("[任务调度] 忽略未登记的任务: {}", job.getJobName());
                continue;
            }
            try {
                registerJob(job);
            } catch (RuntimeException exception) {
                log.error("[任务调度] 固定任务注册失败，继续处理其他任务: jobName={}",
                        job.getJobName(), exception);
            }
        }
        log.info("========== [任务调度] Quartz 持久化任务协调完成，共检查 {} 个任务 ==========", jobs.size());
    }

    /**
     * 幂等注册固定任务，并根据配置同步启用或暂停状态。
     *
     * @param jobConfig 固定任务配置
     */
    public void registerJob(JobConfig jobConfig) {
        if (jobConfig == null || !JOB_CLASSES.containsKey(jobConfig.getJobName())) {
            String jobName = jobConfig == null ? "null" : jobConfig.getJobName();
            throw new IllegalArgumentException("未登记 Quartz Job 类: " + jobName);
        }
        startJob(jobConfig);
        if (!Integer.valueOf(1).equals(jobConfig.getStatus())) {
            pauseJob(jobConfig.getJobName());
        }
    }

    /**
     * 创建或更新一个固定任务的 Quartz Job 和 CronTrigger。
     *
     * @param jobConfig 固定任务配置
     */
    public void startJob(JobConfig jobConfig) {
        Class<? extends Job> jobClass = JOB_CLASSES.get(jobConfig.getJobName());
        if (jobClass == null) {
            throw new IllegalArgumentException("未登记 Quartz Job 类: " + jobConfig.getJobName());
        }
        try {
            JobKey jobKey = jobKey(jobConfig.getJobName());
            JobDetail jobDetail = JobBuilder.newJob(jobClass)
                    .withIdentity(jobKey)
                    .usingJobData("jobName", jobConfig.getJobName())
                    .storeDurably()
                    .build();
            quartzScheduler.addJob(jobDetail, true);

            TriggerKey triggerKey = triggerKey(jobConfig.getJobName());
            Trigger trigger = TriggerBuilder.newTrigger()
                    .withIdentity(triggerKey)
                    .forJob(jobKey)
                    .withSchedule(CronScheduleBuilder.cronSchedule(normalizeLegacyCronExpression(jobConfig))
                            .withMisfireHandlingInstructionDoNothing())
                    .build();
            if (quartzScheduler.checkExists(triggerKey)) {
                quartzScheduler.rescheduleJob(triggerKey, trigger);
            } else {
                try {
                    quartzScheduler.scheduleJob(trigger);
                } catch (ObjectAlreadyExistsException exception) {
                    quartzScheduler.rescheduleJob(triggerKey, trigger);
                }
            }
            quartzScheduler.resumeJob(jobKey);
            log.info("[任务调度] Quartz 启动任务成功: jobName={}, cron={}",
                    jobConfig.getJobName(), jobConfig.getCronExpression());
        } catch (SchedulerException | RuntimeException exception) {
            log.error("[任务调度] Quartz 启动任务失败: {}", jobConfig.getJobName(), exception);
            throw new IllegalStateException("Quartz 注册任务失败: " + jobConfig.getJobName(), exception);
        }
    }

    /**
     * 将旧版同时填写日期和星期的 Cron 表达式转换为 Quartz 兼容格式。
     *
     * @param jobConfig 任务配置
     * @return Quartz 兼容的 Cron 表达式
     */
    private String normalizeLegacyCronExpression(JobConfig jobConfig) {
        String expression = jobConfig.getCronExpression();
        if (expression == null || expression.isBlank()) {
            throw new IllegalArgumentException("任务 Cron 表达式不能为空: " + jobConfig.getJobName());
        }
        String[] fields = expression.trim().split("\\s+");
        if (fields.length < 6) {
            return expression;
        }
        String dayOfMonth = fields[3];
        String dayOfWeek = fields[5];
        if ("?".equals(dayOfMonth) || "?".equals(dayOfWeek)) {
            return expression;
        }
        if ("*".equals(dayOfMonth)) {
            fields[3] = "?";
        } else if ("*".equals(dayOfWeek)) {
            fields[5] = "?";
        } else {
            throw new IllegalArgumentException("Quartz Cron 不能同时指定日期和星期: " + expression);
        }
        String normalized = String.join(" ", fields);
        jobConfig.setCronExpression(normalized);
        jobConfigRepository.save(jobConfig);
        log.warn("[任务调度] 已自动修复旧版 Cron 表达式: jobName={}, oldCron={}, newCron={}",
                jobConfig.getJobName(), expression, normalized);
        return normalized;
    }
    /**
     * 暂停指定任务。
     *
     * @param jobName 任务名称
     */
    public void pauseJob(String jobName) {
        try {
            JobKey key = jobKey(jobName);
            if (quartzScheduler.checkExists(key)) {
                quartzScheduler.pauseJob(key);
                log.info("[任务调度] Quartz 暂停任务成功: {}", jobName);
            }
        } catch (SchedulerException exception) {
            throw new IllegalStateException("暂停任务失败: " + jobName, exception);
        }
    }

    /**
     * 恢复指定任务。
     *
     * @param jobName 任务名称
     */
    public void resumeJob(String jobName) {
        if (!JOB_CLASSES.containsKey(jobName)) {
            throw new IllegalArgumentException("未登记任务: " + jobName);
        }
        try {
            JobKey key = jobKey(jobName);
            if (!quartzScheduler.checkExists(key)) {
                JobConfig config = jobConfigRepository.findByJobName(jobName)
                        .orElseThrow(() -> new IllegalArgumentException("任务不存在: " + jobName));
                startJob(config);
            }
            quartzScheduler.resumeJob(key);
            log.info("[任务调度] Quartz 恢复任务成功: {}", jobName);
        } catch (SchedulerException exception) {
            throw new IllegalStateException("恢复任务失败: " + jobName, exception);
        }
    }

    /**
     * 立即触发一个已经注册的固定任务。
     *
     * @param jobName 任务名称
     * @param runId 执行记录 ID
     * @param source 触发来源
     * @param triggeredBy 触发人
     */
    public void triggerJob(String jobName, String runId, JobTriggerSource source, String triggeredBy) {
        if (!JOB_CLASSES.containsKey(jobName)) {
            throw new IllegalArgumentException("未登记任务: " + jobName);
        }
        try {
            JobKey key = jobKey(jobName);
            if (!quartzScheduler.checkExists(key)) {
                JobConfig config = jobConfigRepository.findByJobName(jobName)
                        .orElseThrow(() -> new IllegalArgumentException("任务不存在: " + jobName));
                startJob(config);
            }
            if (!quartzScheduler.checkExists(key)) {
                throw new IllegalStateException("任务尚未注册到 Quartz: " + jobName);
            }
            JobDataMap dataMap = new JobDataMap();
            dataMap.put("runId", runId);
            dataMap.put("triggerSource", source.name());
            dataMap.put("triggeredBy", triggeredBy);
            quartzScheduler.triggerJob(jobKey(jobName), dataMap);
            log.info("[任务调度] Quartz 手动触发成功: jobName={}, runId={}, operator={}",
                    jobName, runId, triggeredBy);
        } catch (SchedulerException exception) {
            throw new IllegalStateException("触发任务失败: " + jobName, exception);
        }
    }

    /**
     * 请求中断当前正在执行的固定任务。
     *
     * @param jobName 任务名称
     * @return true 表示 Quartz 找到执行实例并发送了中断请求
     */
    public boolean interruptJob(String jobName) {
        if (!JOB_CLASSES.containsKey(jobName)) {
            throw new IllegalArgumentException("未登记任务: " + jobName);
        }
        try {
            return quartzScheduler.interrupt(jobKey(jobName));
        } catch (SchedulerException exception) {
            throw new IllegalStateException("停止任务失败: " + jobName, exception);
        }
    }

    /**
     * 查询任务当前 Quartz 状态。
     *
     * @param jobName 任务名称
     * @return Quartz 运行时状态
     */
    public QuartzRuntimeState getRuntimeState(String jobName) {
        try {
            JobKey jobKey = jobKey(jobName);
            TriggerKey triggerKey = triggerKey(jobName);
            if (!quartzScheduler.checkExists(jobKey)) {
                return new QuartzRuntimeState("NOT_REGISTERED", null, false);
            }
            Trigger.TriggerState state = quartzScheduler.getTriggerState(triggerKey);
            Trigger trigger = quartzScheduler.getTrigger(triggerKey);
            Date nextFireTime = trigger == null ? null : trigger.getNextFireTime();
            LocalDateTime next = nextFireTime == null ? null
                    : LocalDateTime.ofInstant(nextFireTime.toInstant(), ZoneId.systemDefault());
            return new QuartzRuntimeState(state.name(), next, true);
        } catch (SchedulerException exception) {
            throw new IllegalStateException("查询任务调度状态失败: " + jobName, exception);
        }
    }

    private JobKey jobKey(String jobName) {
        return new JobKey(jobName, QUARTZ_GROUP);
    }

    private TriggerKey triggerKey(String jobName) {
        return new TriggerKey(jobName + "-trigger", QUARTZ_GROUP);
    }

    /**
     * Quartz 任务运行时状态。
     *
     * @param triggerState Trigger 状态
     * @param nextFireTime 下一次计划触发时间
     * @param registered 是否已注册
     */
    public record QuartzRuntimeState(String triggerState, LocalDateTime nextFireTime, boolean registered) {
    }
}
// AI_GENERATE_END ----------
