// AI_GENERATE_START-- --------------
package com.stock.tradingExecutor;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.quartz.CronScheduleBuilder;
import org.quartz.Job;
import org.quartz.JobBuilder;
import org.quartz.JobDataMap;
import org.quartz.JobDetail;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.quartz.TriggerKey;
import org.quartz.impl.matchers.GroupMatcher;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

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
            Map.entry("newsDailySync", NewsDailySyncQuartzJob.class),
            Map.entry("tradeCalendarMonthlySync", TradeCalendarMonthlySyncQuartzJob.class),
            Map.entry("lstmOfflineTraining", LstmOfflineTrainingQuartzJob.class),
            Map.entry("dailyModelPrediction", DailyModelPredictionQuartzJob.class),
            Map.entry("modelPredictionEvaluation", ModelPredictionEvaluationQuartzJob.class),
            Map.entry("stockSelection", StockSelectionQuartzJob.class),
            Map.entry("realTradingAutoBuy", RealTradingAutoBuyQuartzJob.class),
            Map.entry("realTradingT1Exit", RealTradingT1ExitQuartzJob.class),
            Map.entry("broker-current-facts-sync", BrokerCurrentFactsSyncQuartzJob.class));

    private final JobConfigRepository jobConfigRepository;
    private final Scheduler quartzScheduler;

    /**
     * 将数据库中的固定任务配置幂等同步到 Quartz 持久化存储。
     */
    public void reconcileAllJobs() {
        log.info("========== [任务调度] 开始协调 Quartz 持久化任务 ==========");
        removeOrphanedJobs();
        List<JobConfig> jobs = jobConfigRepository.findAll();
        for (JobConfig job : jobs) {
            registerJob(job);
        }
        log.info("========== [任务调度] Quartz 持久化任务协调完成，共检查 {} 个任务 ==========", jobs.size());
    }
    /**
     * 删除数据库和 Quartz 中不再属于当前固定任务目录的历史任务。
     *
     * 任务目录由 {@link #JOB_CLASSES} 唯一维护，旧任务不能继续出现在任务管理页面，
     * 也不能在应用启动时阻塞剩余固定任务的注册。
     */
    private void removeOrphanedJobs() {
        List<JobConfig> configuredJobs = jobConfigRepository.findAll();
        List<JobConfig> orphanedJobs = configuredJobs.stream()
                .filter(job -> !JOB_CLASSES.containsKey(job.getJobName()))
                .toList();
        for (JobConfig job : orphanedJobs) {
            jobConfigRepository.deleteByJobName(job.getJobName());
            deleteQuartzJobIfExists(job.getJobName());
            log.info("[任务调度] 已删除历史任务: jobName={}", job.getJobName());
        }
        // 执行记录属于排查审计数据，任务从调度目录移除后仍然保留。
        removeOrphanedQuartzJobs();
    }

    private void deleteQuartzJobIfExists(String jobName) {
        try {
            JobKey key = jobKey(jobName);
            if (quartzScheduler.checkExists(key)) {
                quartzScheduler.deleteJob(key);
            }
        } catch (SchedulerException exception) {
            throw new IllegalStateException("删除历史 Quartz 任务失败: " + jobName, exception);
        }
    }

    private void removeOrphanedQuartzJobs() {
        try {
            Set<JobKey> quartzJobs = quartzScheduler
                    .getJobKeys(GroupMatcher.jobGroupEquals(QUARTZ_GROUP));
            for (JobKey key : quartzJobs) {
                if (!JOB_CLASSES.containsKey(key.getName())) {
                    quartzScheduler.deleteJob(key);
                    log.info("[任务调度] 已删除历史 Quartz 任务: jobName={}", key.getName());
                }
            }
        } catch (SchedulerException exception) {
            throw new IllegalStateException("清理历史 Quartz 任务失败", exception);
        }
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
    private void startJob(JobConfig jobConfig) {
        Class<? extends Job> jobClass = JOB_CLASSES.get(jobConfig.getJobName());
        if (jobClass == null) {
            throw new IllegalArgumentException("未登记 Quartz Job 类: " + jobConfig.getJobName());
        }
        try {
            JobKey jobKey = jobKey(jobConfig.getJobName());
            JobDetail jobDetail = JobBuilder.newJob(jobClass)
                    .withIdentity(jobKey)
                    .storeDurably()
                    .build();
            quartzScheduler.addJob(jobDetail, true);

            TriggerKey triggerKey = triggerKey(jobConfig.getJobName());
            Trigger trigger = TriggerBuilder.newTrigger()
                    .withIdentity(triggerKey)
                    .forJob(jobKey)
                    .withSchedule(CronScheduleBuilder.cronSchedule(jobConfig.getCronExpression())
                            .withMisfireHandlingInstructionDoNothing())
                    .build();
            if (quartzScheduler.checkExists(triggerKey)) {
                quartzScheduler.rescheduleJob(triggerKey, trigger);
            } else {
                quartzScheduler.scheduleJob(trigger);
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
                throw new IllegalStateException("任务尚未注册到 Quartz: " + jobName);
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
            if (!quartzScheduler.checkExists(jobKey) || !quartzScheduler.checkExists(triggerKey)) {
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

    /**
     * 批量读取任务的 Quartz 状态，减少任务列表查询中的重复元数据访问。
     *
     * @param jobNames 任务名称集合
     * @return 任务名称到 Quartz 状态的映射
     */
    public Map<String, QuartzRuntimeState> getRuntimeStates(Collection<String> jobNames) {
        if (jobNames == null || jobNames.isEmpty()) {
            return Map.of();
        }
        try {
            Set<String> registeredJobs = quartzScheduler
                    .getJobKeys(GroupMatcher.jobGroupEquals(QUARTZ_GROUP))
                    .stream()
                    .map(JobKey::getName)
                    .collect(Collectors.toSet());
            Map<String, QuartzRuntimeState> states = new HashMap<>();
            for (String jobName : jobNames) {
                JobKey jobKey = jobKey(jobName);
                TriggerKey triggerKey = triggerKey(jobName);
                if (!registeredJobs.contains(jobName)
                        || !quartzScheduler.checkExists(triggerKey)
                        || !quartzScheduler.checkExists(jobKey)) {
                    states.put(jobName, new QuartzRuntimeState("NOT_REGISTERED", null, false));
                    continue;
                }
                Trigger.TriggerState state = quartzScheduler.getTriggerState(triggerKey);
                states.put(jobName, new QuartzRuntimeState(state.name(), null, true));
            }
            return states;
        } catch (SchedulerException exception) {
            throw new IllegalStateException("批量查询任务调度状态失败", exception);
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
// AI_GENERATE_END ---------------
