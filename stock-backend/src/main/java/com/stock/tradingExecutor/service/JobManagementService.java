// AI_GENERATE_START- --------
package com.stock.tradingExecutor.service;

import com.stock.tradingExecutor.domain.dto.JobManagementDto;
import com.stock.tradingExecutor.JobConfig;
import com.stock.tradingExecutor.JobConfigRepository;
import com.stock.tradingExecutor.JobExecutionRecord;
import com.stock.tradingExecutor.JobExecutionService;
import com.stock.tradingExecutor.JobSchedulerService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 任务查询和调度操作服务。
 * 负责组合 JobConfig、Quartz 状态和独立执行记录，不执行具体业务任务。
 *
 * @author mwangli
 * @since 2026-10-11
 */
@Service
@RequiredArgsConstructor
public class JobManagementService {

    private final JobConfigRepository jobConfigRepository;
    private final JobSchedulerService jobSchedulerService;
    private final JobExecutionService jobExecutionService;

    /**
     * 查询所有固定任务的调度和最近一次执行状态。
     *
     * @return 任务状态列表
     */
    @Transactional(readOnly = true)
    public JobManagementDto.JobListResponse listJobs() {
        List<JobConfig> jobs = jobConfigRepository.findAll();
        List<String> jobNames = jobs.stream().map(JobConfig::getJobName).toList();
        Map<String, JobExecutionRecord> latestRuns = jobExecutionService.getLatestRuns(jobNames).stream()
                .collect(Collectors.toMap(JobExecutionRecord::getJobName, Function.identity(),
                        (first, ignored) -> first));
        Map<String, JobSchedulerService.QuartzRuntimeState> runtimeStates =
                jobSchedulerService.getRuntimeStates(jobNames);
        List<JobManagementDto.JobItem> items = jobs.stream()
                .map(job -> toJobItem(job, runtimeStates.get(job.getJobName()), latestRuns.get(job.getJobName())))
                .toList();
        return JobManagementDto.JobListResponse.builder().items(items).build();
    }

    /**
     * 查询指定任务状态。
     *
     * @param jobName 任务名称
     * @return 任务状态
     */
    @Transactional(readOnly = true)
    public JobManagementDto.JobItem getJob(String jobName) {
        return toJobItem(jobConfigRepository.findByJobName(jobName)
                .orElseThrow(() -> new IllegalArgumentException("任务不存在: " + jobName)));
    }

    /**
     * 分页查询执行记录。
     *
     * @param jobName 任务名称，可为空
     * @param status 状态，可为空
     * @param source 来源，可为空
     * @param from 开始时间，可为空
     * @param to 结束时间，可为空
     * @param current 当前页码
     * @param pageSize 每页大小
     * @return 执行记录分页结果
     */
    @Transactional(readOnly = true)
    public JobManagementDto.ExecutionPageResponse listRuns(String jobName,
                                                            com.stock.tradingExecutor.JobExecutionStatus status,
                                                            com.stock.tradingExecutor.JobTriggerSource source,
                                                            LocalDateTime from, LocalDateTime to,
                                                            int current, int pageSize) {
        Page<JobExecutionRecord> page = jobExecutionService.queryRuns(
                jobName, status, source, from, to, current, pageSize);
        return JobManagementDto.ExecutionPageResponse.builder()
                .items(page.getContent().stream().map(this::toExecutionItem).toList())
                .total(page.getTotalElements())
                .current(Math.max(current, 1))
                .pageSize(Math.min(Math.max(pageSize, 1), 100))
                .build();
    }

    /**
     * 查询执行记录详情。
     *
     * @param runId 运行标识
     * @return 执行记录详情
     */
    @Transactional(readOnly = true)
    public JobManagementDto.ExecutionItem getRun(String runId) {
        return toExecutionItem(jobExecutionService.getRun(runId));
    }

    /**
     * 触发固定任务。
     *
     * @param jobName 任务名称
     * @param operator 操作人
     * @return 触发结果
     */
    public JobManagementDto.TriggerResponse trigger(String jobName, String operator) {
        String runId = jobExecutionService.triggerManually(jobName, operator);
        return JobManagementDto.TriggerResponse.builder()
                .runId(runId)
                .message("任务已开始执行")
                .build();
    }

    /**
     * 停止正在执行的任务运行。
     *
     * @param runId 运行标识
     * @param operator 操作人
     * @return 停止结果
     */
    public JobManagementDto.StopResponse stop(String runId, String operator) {
        return jobExecutionService.stopRun(runId, operator);
    }

    /**
     * 暂停任务并保存禁用状态。
     *
     * @param jobName 任务名称
     */
    @Transactional
    public void pause(String jobName) {
        JobConfig job = getConfig(jobName);
        job.setStatus(0);
        jobConfigRepository.save(job);
        jobSchedulerService.pauseJob(jobName);
    }

    /**
     * 恢复任务并保存启用状态。
     *
     * @param jobName 任务名称
     */
    @Transactional
    public void resume(String jobName) {
        JobConfig job = getConfig(jobName);
        job.setStatus(1);
        jobConfigRepository.save(job);
        jobSchedulerService.resumeJob(jobName);
    }

    private JobManagementDto.JobItem toJobItem(JobConfig job) {
        JobSchedulerService.QuartzRuntimeState runtime = jobSchedulerService.getRuntimeState(job.getJobName());
        JobExecutionRecord latestRun = jobExecutionService.getLatestRun(job.getJobName());
        return toJobItem(job, runtime, latestRun);
    }

    private JobManagementDto.JobItem toJobItem(JobConfig job,
                                               JobSchedulerService.QuartzRuntimeState runtime,
                                               JobExecutionRecord latestRun) {
        return JobManagementDto.JobItem.builder()
                .jobName(job.getJobName())
                .methodName(job.getMethodName())
                .description(job.getDescription())
                .cronExpression(job.getCronExpression())
                .module(job.getModule())
                .syncMode(job.getSyncMode())
                .enabled(Integer.valueOf(1).equals(job.getStatus()))
                .taskStatus(resolveTaskStatus(job, runtime))
                .latestRun(toExecutionItem(latestRun))
                .build();
    }

    private String resolveTaskStatus(JobConfig job, JobSchedulerService.QuartzRuntimeState runtime) {
        if (!Integer.valueOf(1).equals(job.getStatus())) {
            return "STOPPED";
        }
        if (runtime == null || !runtime.registered()) {
            return "NOT_REGISTERED";
        }
        if ("PAUSED".equals(runtime.triggerState())) {
            return "PAUSED";
        }
        if ("ERROR".equals(runtime.triggerState())) {
            return "ERROR";
        }
        return "ENABLED";
    }

    private JobConfig getConfig(String jobName) {
        return jobConfigRepository.findByJobName(jobName)
                .orElseThrow(() -> new IllegalArgumentException("任务不存在: " + jobName));
    }

    private JobManagementDto.ExecutionItem toExecutionItem(JobExecutionRecord record) {
        if (record == null) {
            return null;
        }
        return JobManagementDto.ExecutionItem.builder()
                .runId(record.getRunId())
                .jobName(record.getJobName())
                .triggerSource(record.getTriggerSource().name())
                .triggeredBy(record.getTriggeredBy())
                .status(record.getStatus() == com.stock.tradingExecutor.JobExecutionStatus.QUEUED
                        ? com.stock.tradingExecutor.JobExecutionStatus.RUNNING.name()
                        : record.getStatus().name())
                .scheduledTime(record.getScheduledTime())
                .queuedTime(record.getQueuedTime())
                .startedTime(record.getStartedTime())
                .finishedTime(record.getFinishedTime())
                .durationMs(record.getDurationMs())
                .errorType(record.getErrorType())
                .errorMessage(record.getErrorMessage())
                .resultSummary(record.getResultSummary())
                .hostName(record.getHostName())
                .createdAt(record.getCreatedAt())
                .build();
    }
}
// AI_GENERATE_END -------
