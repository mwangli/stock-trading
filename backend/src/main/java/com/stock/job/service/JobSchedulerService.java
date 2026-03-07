package com.stock.job.service;

import com.stock.job.entity.JobConfig;
import com.stock.job.repository.JobConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Service;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

@Slf4j
@Service
@RequiredArgsConstructor
public class JobSchedulerService {

    private final JobConfigRepository jobConfigRepository;
    private final ApplicationContext applicationContext;
    private final ThreadPoolTaskScheduler taskScheduler;

    private final Map<String, ScheduledFuture<?>> scheduledTasks = new ConcurrentHashMap<>();

    /**
     * 初始化任务调度
     */
    public void startAllActiveJobs() {
        log.info("========== [任务调度] 开始加载系统任务 ==========");
        List<JobConfig> jobs = jobConfigRepository.findAll();
        for (JobConfig job : jobs) {
            if (Integer.valueOf(1).equals(job.getStatus())) {
                startJob(job);
            } else {
                log.info("[任务调度] 任务 {} 状态为禁用，跳过启动", job.getJobName());
            }
        }
        log.info("========== [任务调度] 任务加载完成，共启动 {} 个任务 ==========", scheduledTasks.size());
    }

    /**
     * 启动单个任务
     */
    public void startJob(JobConfig jobConfig) {
        String jobName = jobConfig.getJobName();
        if (scheduledTasks.containsKey(jobName)) {
            stopJob(jobName);
        }

        try {
            Runnable task = createRunnable(jobConfig);
            ScheduledFuture<?> future = taskScheduler.schedule(task, new CronTrigger(jobConfig.getCronExpression()));
            scheduledTasks.put(jobName, future);
            log.info("[任务调度] 启动任务成功: {} (Cron: {})", jobName, jobConfig.getCronExpression());
        } catch (Exception e) {
            log.error("[任务调度] 启动任务失败: {}", jobName, e);
        }
    }

    /**
     * 停止单个任务
     */
    public void stopJob(String jobName) {
        ScheduledFuture<?> future = scheduledTasks.get(jobName);
        if (future != null) {
            future.cancel(true);
            scheduledTasks.remove(jobName);
            log.info("[任务调度] 停止任务成功: {}", jobName);
        }
    }

    /**
     * 立即运行一次任务
     */
    public void runJobNow(Long jobId) {
        Optional<JobConfig> jobOpt = jobConfigRepository.findById(jobId);
        if (jobOpt.isEmpty()) {
            throw new RuntimeException("任务不存在");
        }
        
        JobConfig jobConfig = jobOpt.get();
        log.info("[任务调度] 手动触发任务: {}", jobConfig.getJobName());
        
        // 异步执行，避免阻塞接口
        new Thread(createRunnable(jobConfig)).start();
    }
    
    /**
     * 更新任务Cron表达式
     */
    public void updateJobCron(Long jobId, String newCron) {
        JobConfig jobConfig = jobConfigRepository.findById(jobId)
                .orElseThrow(() -> new RuntimeException("任务不存在"));
        
        jobConfig.setCronExpression(newCron);
        jobConfigRepository.save(jobConfig);
        
        if (Integer.valueOf(1).equals(jobConfig.getStatus())) {
            startJob(jobConfig);
        }
    }
    
    /**
     * 切换任务状态（启动/暂停）
     */
    public void toggleJobStatus(Long jobId, boolean active) {
        JobConfig jobConfig = jobConfigRepository.findById(jobId)
                .orElseThrow(() -> new RuntimeException("任务不存在"));
        
        jobConfig.setStatus(active ? 1 : 0);
        jobConfigRepository.save(jobConfig);
        
        if (active) {
            startJob(jobConfig);
        } else {
            stopJob(jobConfig.getJobName());
        }
    }

    /**
     * 创建任务执行逻辑
     */
    private Runnable createRunnable(JobConfig jobConfig) {
        return () -> {
            long startTime = System.currentTimeMillis();
            try {
                log.info("[任务执行] 开始执行任务: {}", jobConfig.getJobName());
                
                Object bean = applicationContext.getBean(jobConfig.getBeanName());
                Method method = bean.getClass().getMethod(jobConfig.getMethodName());
                method.invoke(bean);
                
                long costTime = System.currentTimeMillis() - startTime;
                log.info("[任务执行] 任务执行成功: {}, 耗时: {}ms", jobConfig.getJobName(), costTime);
                
                // 更新执行状态
                updateJobExecutionStatus(jobConfig.getId(), 1, null, costTime);
                
            } catch (Exception e) {
                long costTime = System.currentTimeMillis() - startTime;
                log.error("[任务执行] 任务执行失败: {}", jobConfig.getJobName(), e);
                
                // 更新执行状态
                updateJobExecutionStatus(jobConfig.getId(), 0, e.getMessage(), costTime);
            }
        };
    }

    private void updateJobExecutionStatus(Long jobId, Integer status, String errorMsg, Long costTime) {
        try {
            JobConfig job = jobConfigRepository.findById(jobId).orElse(null);
            if (job != null) {
                job.setLastRunTime(LocalDateTime.now());
                job.setLastStatus(status);
                job.setLastCostTime(costTime);
                job.setErrorMessage(errorMsg);
                jobConfigRepository.save(job);
            }
        } catch (Exception e) {
            log.error("[任务调度] 更新任务状态失败", e);
        }
    }
}
