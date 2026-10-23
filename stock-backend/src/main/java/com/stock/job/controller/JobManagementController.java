// AI_GENERATE_START-------
package com.stock.job.controller;

import com.stock.base.api.ResponseDTO;
import com.stock.job.dto.JobExecutionPageResponse;
import com.stock.job.dto.JobExecutionResponse;
import com.stock.job.dto.JobItemResponse;
import com.stock.job.dto.JobListResponse;
import com.stock.job.dto.JobStopResponse;
import com.stock.job.dto.JobTriggerResponse;
import com.stock.job.enums.JobExecutionStatus;
import com.stock.job.enums.JobTriggerSource;
import com.stock.job.service.JobManagementService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

/**
 * Quartz 固定任务运维接口。
 * 提供任务状态、执行历史、白名单触发和暂停恢复能力。
 *
 * @author mwangli
 * @since 2026-10-11
 */
@Slf4j
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class JobManagementController {

    private final JobManagementService jobManagementService;

    /**
     * 查询全部固定任务状态。
     *
     * @return 任务状态列表
     */
    @GetMapping("/jobs")
    public ResponseDTO<JobListResponse> listJobs() {
        log.info("[JobManagement] 查询 Quartz 任务状态");
        return ResponseDTO.success(jobManagementService.listJobs());
    }

    /**
     * 查询指定任务状态。
     *
     * @param jobName 任务名称
     * @return 任务状态
     */
    @GetMapping("/jobs/details/{jobName}")
    public ResponseDTO<JobItemResponse> getJob(@PathVariable String jobName) {
        log.info("[JobManagement] 查询任务详情: jobName={}", jobName);
        return ResponseDTO.success(jobManagementService.getJob(jobName));
    }

    /**
     * 分页查询任务执行历史。
     *
     * @param jobName 任务名称
     * @param status 执行状态
     * @param source 触发来源
     * @param from 开始时间
     * @param to 结束时间
     * @param current 当前页码
     * @param pageSize 每页大小
     * @return 执行历史分页结果
     */
    @GetMapping("/jobRuns")
    public ResponseDTO<JobExecutionPageResponse> listRuns(
            @RequestParam(required = false) String jobName,
            @RequestParam(required = false) JobExecutionStatus status,
            @RequestParam(required = false) JobTriggerSource source,
            @RequestParam(required = false) LocalDateTime from,
            @RequestParam(required = false) LocalDateTime to,
            @RequestParam(defaultValue = "1") int current,
            @RequestParam(defaultValue = "20") int pageSize) {
        log.info("[JobManagement] 查询执行历史: jobName={}, status={}, source={}, current={}, pageSize={}",
                jobName, status, source, current, pageSize);
        return ResponseDTO.success(jobManagementService.listRuns(
                jobName, status, source, from, to, current, pageSize));
    }

    /**
     * 查询执行记录详情。
     *
     * @param runId 运行标识
     * @return 执行记录详情
     */
    @GetMapping("/jobRuns/details/{runId}")
    public ResponseDTO<JobExecutionResponse> getRun(@PathVariable String runId) {
        log.info("[JobManagement] 查询执行记录详情: runId={}", runId);
        return ResponseDTO.success(jobManagementService.getRun(runId));
    }

    /**
     * 受控手动触发固定任务。
     *
     * @param jobName 任务名称
     * @param operator 操作人
     * @return 触发结果
     */
    @PostMapping("/jobs/trigger/{jobName}")
    public ResponseDTO<JobTriggerResponse> trigger(
            @PathVariable String jobName,
            @RequestParam(required = false) String operator) {
        log.info("[JobManagement] 手动触发任务: jobName={}, operator={}", jobName, operator);
        return ResponseDTO.success(jobManagementService.trigger(jobName, operator));
    }

    /**
     * 停止正在执行的任务运行。
     *
     * @param runId 运行标识
     * @param operator 操作人
     * @return 停止结果
     */
    @PostMapping("/jobRuns/stop/{runId}")
    public ResponseDTO<JobStopResponse> stop(
            @PathVariable String runId,
            @RequestParam(required = false) String operator) {
        log.info("[JobManagement] 停止任务执行: runId={}, operator={}", runId, operator);
        return ResponseDTO.success(jobManagementService.stop(runId, operator));
    }

    /**
     * 暂停固定任务。
     *
     * @param jobName 任务名称
     * @return 空数据成功响应
     */
    @PostMapping("/jobs/pause/{jobName}")
    public ResponseDTO<Void> pause(@PathVariable String jobName) {
        log.info("[JobManagement] 暂停任务: jobName={}", jobName);
        jobManagementService.pause(jobName);
        return ResponseDTO.success(null);
    }

    /**
     * 恢复固定任务。
     *
     * @param jobName 任务名称
     * @return 空数据成功响应
     */
    @PostMapping("/jobs/resume/{jobName}")
    public ResponseDTO<Void> resume(@PathVariable String jobName) {
        log.info("[JobManagement] 恢复任务: jobName={}", jobName);
        jobManagementService.resume(jobName);
        return ResponseDTO.success(null);
    }
}
// AI_GENERATE_END- ---

// AI_GENERATE_END-------
