// AI_GENERATE_START--------------------
package com.stock.job.service;

import com.stock.job.enums.JobExecutionStatus;
import com.stock.job.enums.JobTriggerSource;
import com.stock.job.repository.JobConfigRepository;

import com.stock.job.dto.JobExecutionPageResponse;
import com.stock.job.dto.JobExecutionResponse;
import com.stock.job.dto.JobItemResponse;
import com.stock.job.dto.JobListResponse;
import com.stock.job.dto.JobStopResponse;
import com.stock.job.dto.JobTriggerResponse;
import com.stock.job.entity.JobConfig;
import com.stock.job.entity.JobExecutionRecord;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
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
    public JobListResponse listJobs() {
        List<JobConfig> jobs = jobConfigRepository.findAll();
        List<String> jobNames = jobs.stream().map(JobConfig::getJobName).toList();
        Map<String, JobExecutionRecord> latestRuns = jobExecutionService.getLatestRuns(jobNames).stream()
                .collect(Collectors.toMap(JobExecutionRecord::getJobName, Function.identity(),
                        (first, ignored) -> first));
        Map<String, JobSchedulerService.QuartzRuntimeState> runtimeStates =
                jobSchedulerService.getRuntimeStates(jobNames);
        List<JobItemResponse> items = jobs.stream()
                .map(job -> toJobItem(job, runtimeStates.get(job.getJobName()), latestRuns.get(job.getJobName())))
                .toList();
        return JobListResponse.builder().items(items).build();
    }

    /**
     * 查询指定任务状态。
     *
     * @param jobName 任务名称
     * @return 任务状态
     */
    public JobItemResponse getJob(String jobName) {
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
    public JobExecutionPageResponse listRuns(String jobName,
                                                            com.stock.job.enums.JobExecutionStatus status,
                                                            com.stock.job.enums.JobTriggerSource source,
                                                            LocalDateTime from, LocalDateTime to,
                                                            int current, int pageSize) {
        Page<JobExecutionRecord> page = jobExecutionService.queryRuns(
                jobName, status, source, from, to, current, pageSize);
        return JobExecutionPageResponse.builder()
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
    public JobExecutionResponse getRun(String runId) {
        return toExecutionItem(jobExecutionService.getRun(runId));
    }

    /**
     * 触发固定任务。
     *
     * @param jobName 任务名称
     * @param operator 操作人
     * @return 触发结果
     */
    public JobTriggerResponse trigger(String jobName, String operator) {
        return jobExecutionService.triggerManually(jobName, operator);
    }

    /**
     * 停止正在执行的任务运行。
     *
     * @param runId 运行标识
     * @param operator 操作人
     * @return 停止结果
     */
    public JobStopResponse stop(String runId, String operator) {
        return jobExecutionService.stopRun(runId, operator);
    }

    /**
     * 暂停任务并保存禁用状态。
     *
     * @param jobName 任务名称
     */
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
    public void resume(String jobName) {
        JobConfig job = getConfig(jobName);
        job.setStatus(1);
        jobConfigRepository.save(job);
        jobSchedulerService.resumeJob(jobName);
    }

    private JobItemResponse toJobItem(JobConfig job) {
        JobSchedulerService.QuartzRuntimeState runtime = jobSchedulerService.getRuntimeState(job.getJobName());
        JobExecutionRecord latestRun = jobExecutionService.getLatestRun(job.getJobName());
        return toJobItem(job, runtime, latestRun);
    }

    private JobItemResponse toJobItem(JobConfig job,
                                               JobSchedulerService.QuartzRuntimeState runtime,
                                               JobExecutionRecord latestRun) {
        return JobItemResponse.builder()
                .jobName(job.getJobName())
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

    private JobExecutionResponse toExecutionItem(JobExecutionRecord record) {
        if (record == null) {
            return null;
        }
        return JobExecutionResponse.builder()
                .runId(record.getRunId())
                .jobName(record.getJobName())
                .triggerSource(record.getTriggerSource().name())
                .triggeredBy(record.getTriggeredBy())
                .status(record.getStatus().name())
                .scheduledTime(record.getScheduledTime())
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
// AI_GENERATE_END--------------------
