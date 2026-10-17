// AI_GENERATE_START ------
package com.stock.tradingExecutor.job;

import com.stock.tradingExecutor.domain.dto.JobManagementDto;
import com.stock.tradingExecutor.runtime.service.SystemRuntimeConfigService;
import jakarta.transaction.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.net.InetAddress;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 统一任务执行记录服务。
 * 直接接收固定 Quartz Job 的业务执行回调，不通过反射分派任务。
 *
 * @author mwangli
 * @since 2026-10-11
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JobExecutionService {

    private static final Set<JobExecutionStatus> ACTIVE_STATUSES = EnumSet.of(
            JobExecutionStatus.QUEUED,
            JobExecutionStatus.RUNNING);

    private final Set<String> cancellationRequests = ConcurrentHashMap.newKeySet();

    private final JobConfigRepository jobConfigRepository;
    private final JobExecutionRecordRepository executionRecordRepository;
    private final JobSchedulerService jobSchedulerService;
    private final SystemRuntimeConfigService runtimeConfigService;

    /**
     * 手动触发已注册且启用的固定任务。
     *
     * @param jobName 固定任务名称
     * @param operator 操作人
     * @return 新建的运行标识
     */
    @Transactional
    public String triggerManually(String jobName, String operator) {
        if (!runtimeConfigService.isSchedulerEnabled()) {
            throw new IllegalStateException("系统配置已关闭统一任务调度");
        }
        JobConfig jobConfig = jobConfigRepository.findByJobName(jobName)
                .orElseThrow(() -> new IllegalArgumentException("任务不存在: " + jobName));
        if (!Integer.valueOf(1).equals(jobConfig.getStatus())) {
            throw new IllegalStateException("任务当前未启用: " + jobName);
        }
        if (executionRecordRepository.existsByJobNameAndStatusIn(jobName, ACTIVE_STATUSES)) {
            throw new IllegalStateException("任务已有排队或运行中的执行: " + jobName);
        }

        String runId = UUID.randomUUID().toString();
        JobExecutionRecord record;
        try {
            record = createQueuedRecord(jobName, JobTriggerSource.MANUAL,
                    normalizeOperator(operator), null, runId);
        } catch (DataIntegrityViolationException exception) {
            throw new IllegalStateException("任务已有排队或运行中的执行: " + jobName, exception);
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    jobSchedulerService.triggerJob(jobName, record.getRunId(), JobTriggerSource.MANUAL,
                            record.getTriggeredBy());
                } catch (RuntimeException exception) {
                    markFailure(record, exception, LocalDateTime.now());
                    log.error("[任务执行] 手动任务提交 Quartz 失败: jobName={}, runId={}",
                            jobName, record.getRunId(), exception);
                }
            }
        });
        return runId;
    }

    /**
     * 执行固定 Quartz Job 提交的业务回调并保存完整运行记录。
     *
     * @param jobName 固定任务名称
     * @param source 触发来源
     * @param triggeredBy 触发人
     * @param runId 已有运行标识，定时触发时可为空
     * @param scheduledFireTime Quartz 计划触发时间
     * @param action 固定 Quartz Job 提供的业务执行回调
     */
    public void execute(String jobName, JobTriggerSource source, String triggeredBy,
                        String runId, Date scheduledFireTime, Runnable action) {
        JobExecutionRecord record;
        if (runId == null || runId.isBlank()) {
            try {
                record = createQueuedRecord(jobName, source, normalizeOperator(triggeredBy), scheduledFireTime, null);
            } catch (DataIntegrityViolationException exception) {
                log.info("[任务执行] 检测到已有活动执行，跳过重复定时触发: jobName={}", jobName);
                return;
            }
        } else {
            record = executionRecordRepository.findByRunId(runId)
                    .orElseGet(() -> createQueuedRecord(jobName, source, normalizeOperator(triggeredBy),
                            scheduledFireTime, runId));
        }
        if (!runtimeConfigService.isSchedulerEnabled()) {
            markCancelled(record, "系统配置已关闭统一任务调度");
            return;
        }
        if (isCancellationRequested(record)) {
            markCancelled(record, "任务在开始执行前已被停止");
            cancellationRequests.remove(record.getRunId());
            return;
        }
        LocalDateTime startedAt = LocalDateTime.now();
        record.setStatus(JobExecutionStatus.RUNNING).setStartedTime(startedAt);
        executionRecordRepository.save(record);
        log.info("[任务执行] 开始执行任务: jobName={}, runId={}, source={}",
                jobName, record.getRunId(), source);

        try {
            action.run();
            if (isCancellationRequested(record) || Thread.currentThread().isInterrupted()) {
                markCancelled(record, "任务执行期间收到停止请求");
            } else {
                markSuccess(record, startedAt);
            }
        } catch (JobSkippedException exception) {
            markSkipped(record, exception.getMessage(), startedAt);
        } catch (Exception exception) {
            if (isCancellationRequested(record) || Thread.currentThread().isInterrupted()) {
                markCancelled(record, "任务执行期间收到停止请求");
            } else {
                markFailure(record, exception, startedAt);
            }
        } finally {
            cancellationRequests.remove(record.getRunId());
        }
    }

    /**
     * 停止排队中或执行中的任务运行。
     *
     * @param runId 运行标识
     * @param operator 操作人
     * @return 停止结果
     */
    public JobManagementDto.StopResponse stopRun(String runId, String operator) {
        JobExecutionRecord record = getRun(runId);
        if (!ACTIVE_STATUSES.contains(record.getStatus())) {
            throw new IllegalStateException("任务运行已结束，不能重复停止: " + runId);
        }

        JobExecutionStatus previousStatus = record.getStatus();
        String normalizedOperator = normalizeOperator(operator);
        if (previousStatus == JobExecutionStatus.RUNNING) {
            cancellationRequests.add(runId);
        }
        markCancelled(record, "由 " + normalizedOperator + " 请求停止");

        boolean interrupted = false;
        String message = "排队任务已取消";
        if (previousStatus == JobExecutionStatus.RUNNING) {
            try {
                interrupted = jobSchedulerService.interruptJob(record.getJobName());
                message = interrupted ? "已发送任务中断请求" : "任务已标记取消，但未找到可中断的执行线程";
            } catch (RuntimeException exception) {
                log.warn("[任务执行] 任务已标记取消，但发送中断请求失败: jobName={}, runId={}",
                        record.getJobName(), runId, exception);
                message = "任务已标记取消，但发送中断请求失败";
            }
        }
        return JobManagementDto.StopResponse.builder()
                .runId(runId)
                .status(JobExecutionStatus.CANCELLED.name())
                .interrupted(interrupted)
                .message(message)
                .build();
    }

    /**
     * 分页查询任务执行历史。
     *
     * @param jobName 任务名称，可为空
     * @param status 运行状态，可为空
     * @param source 触发来源，可为空
     * @param from 开始时间，可为空
     * @param to 结束时间，可为空
     * @param current 当前页码，从 1 开始
     * @param pageSize 每页大小
     * @return 执行历史分页结果
     */
    @Transactional
    public Page<JobExecutionRecord> queryRuns(String jobName, JobExecutionStatus status,
                                              JobTriggerSource source, LocalDateTime from,
                                              LocalDateTime to, int current, int pageSize) {
        int safeCurrent = Math.max(current, 1);
        int safePageSize = Math.min(Math.max(pageSize, 1), 100);
        Specification<JobExecutionRecord> specification = Specification.where(null);
        if (jobName != null && !jobName.isBlank()) {
            specification = specification.and((root, query, cb) -> cb.equal(root.get("jobName"), jobName));
        }
        if (status != null) {
            specification = specification.and((root, query, cb) -> cb.equal(root.get("status"), status));
        }
        if (source != null) {
            specification = specification.and((root, query, cb) -> cb.equal(root.get("triggerSource"), source));
        }
        if (from != null) {
            specification = specification.and((root, query, cb) ->
                    cb.greaterThanOrEqualTo(root.get("createdAt"), from));
        }
        if (to != null) {
            specification = specification.and((root, query, cb) ->
                    cb.lessThanOrEqualTo(root.get("createdAt"), to));
        }
        Pageable pageable = PageRequest.of(safeCurrent - 1, safePageSize,
                Sort.by(Sort.Direction.DESC, "createdAt"));
        return executionRecordRepository.findAll(specification, pageable);
    }

    /**
     * 查询指定运行记录。
     *
     * @param runId 运行标识
     * @return 运行记录
     */
    @Transactional
    public JobExecutionRecord getRun(String runId) {
        return executionRecordRepository.findByRunId(runId)
                .orElseThrow(() -> new IllegalArgumentException("运行记录不存在: " + runId));
    }

    /**
     * 查询指定任务最近一次运行记录。
     *
     * @param jobName 任务名称
     * @return 最近一次运行记录，可为空
     */
    @Transactional
    public JobExecutionRecord getLatestRun(String jobName) {
        return executionRecordRepository.findTopByJobNameOrderByCreatedAtDesc(jobName).orElse(null);
    }

    private JobExecutionRecord createQueuedRecord(String jobName, JobTriggerSource source,
                                                   String operator, Date scheduledFireTime,
                                                   String runId) {
        LocalDateTime now = LocalDateTime.now();
        JobExecutionRecord record = new JobExecutionRecord()
                .setRunId(runId == null ? UUID.randomUUID().toString() : runId)
                .setJobName(jobName)
                .setTriggerSource(source)
                .setTriggeredBy(operator)
                .setActiveLockKey(jobName)
                .setStatus(JobExecutionStatus.QUEUED)
                .setScheduledTime(toLocalDateTime(scheduledFireTime))
                .setQueuedTime(now)
                .setCreatedAt(now)
                .setHostName(resolveHostName());
        return executionRecordRepository.save(record);
    }

    private void markSuccess(JobExecutionRecord record, LocalDateTime startedAt) {
        LocalDateTime finishedAt = LocalDateTime.now();
        long duration = java.time.Duration.between(startedAt, finishedAt).toMillis();
        record.setStatus(JobExecutionStatus.SUCCESS)
                .setActiveLockKey(null)
                .setFinishedTime(finishedAt)
                .setDurationMs(duration)
                .setResultSummary("任务执行完成");
        executionRecordRepository.save(record);
        updateLatestJobStatus(record, 1, null, duration);
        log.info("[任务执行] 任务执行成功: jobName={}, runId={}, durationMs={}",
                record.getJobName(), record.getRunId(), duration);
    }

    private void markFailure(JobExecutionRecord record, Throwable throwable, LocalDateTime startedAt) {
        LocalDateTime finishedAt = LocalDateTime.now();
        long duration = java.time.Duration.between(startedAt, finishedAt).toMillis();
        record.setStatus(JobExecutionStatus.FAILED)
                .setActiveLockKey(null)
                .setFinishedTime(finishedAt)
                .setDurationMs(duration)
                .setErrorType(throwable.getClass().getName())
                .setErrorMessage(safeMessage(throwable));
        executionRecordRepository.save(record);
        updateLatestJobStatus(record, 0, safeMessage(throwable), duration);
        log.error("[任务执行] 任务执行失败: jobName={}, runId={}",
                record.getJobName(), record.getRunId(), throwable);
    }

    private void markSkipped(JobExecutionRecord record, String summary, LocalDateTime startedAt) {
        LocalDateTime finishedAt = LocalDateTime.now();
        long duration = java.time.Duration.between(startedAt, finishedAt).toMillis();
        record.setStatus(JobExecutionStatus.SKIPPED)
                .setActiveLockKey(null)
                .setFinishedTime(finishedAt)
                .setDurationMs(duration)
                .setResultSummary(summary);
        executionRecordRepository.save(record);
        updateLatestJobStatus(record, 1, null, duration);
        log.info("[任务执行] 任务按业务条件跳过: jobName={}, runId={}, summary={}",
                record.getJobName(), record.getRunId(), summary);
    }

    private void markCancelled(JobExecutionRecord record, String summary) {
        JobExecutionRecord current = executionRecordRepository.findByRunId(record.getRunId()).orElse(record);
        LocalDateTime finishedAt = current.getFinishedTime() != null
                ? current.getFinishedTime()
                : LocalDateTime.now();
        LocalDateTime durationStart = current.getStartedTime() != null
                ? current.getStartedTime()
                : current.getQueuedTime();
        long duration = durationStart == null
                ? 0L
                : Math.max(0L, java.time.Duration.between(durationStart, finishedAt).toMillis());
        current.setStatus(JobExecutionStatus.CANCELLED)
                .setActiveLockKey(null)
                .setFinishedTime(finishedAt)
                .setDurationMs(duration)
                .setResultSummary(summary);
        executionRecordRepository.save(current);
        log.info("[任务执行] 任务已取消: jobName={}, runId={}, summary={}",
                current.getJobName(), current.getRunId(), summary);
    }

    private boolean isCancellationRequested(JobExecutionRecord record) {
        if (cancellationRequests.contains(record.getRunId())) {
            return true;
        }
        return executionRecordRepository.findByRunId(record.getRunId())
                .map(item -> item.getStatus() == JobExecutionStatus.CANCELLED)
                .orElse(false);
    }

    private void updateLatestJobStatus(JobExecutionRecord record, int status,
                                       String errorMessage, long duration) {
        jobConfigRepository.findByJobName(record.getJobName()).ifPresent(job -> {
            job.setLastRunTime(record.getFinishedTime())
                    .setLastStatus(status)
                    .setLastCostTime(duration)
                    .setErrorMessage(errorMessage);
            jobConfigRepository.save(job);
        });
    }

    private String safeMessage(Throwable throwable) {
        String message = throwable.getMessage();
        return message == null || message.isBlank() ? throwable.getClass().getSimpleName() : message;
    }

    private String normalizeOperator(String operator) {
        return operator == null || operator.isBlank() ? "system" : operator.trim();
    }

    private LocalDateTime toLocalDateTime(Date value) {
        return value == null ? null : LocalDateTime.ofInstant(value.toInstant(), ZoneId.systemDefault());
    }

    private String resolveHostName() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (Exception exception) {
            return "unknown-host";
        }
    }

}
// AI_GENERATE_END ------
