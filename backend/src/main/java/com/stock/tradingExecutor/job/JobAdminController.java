package com.stock.tradingExecutor.job;

import com.stock.common.dto.ResponseDTO;
import com.stock.tradingExecutor.domain.dto.JobToggleStatusRequestDto;
import com.stock.tradingExecutor.domain.dto.JobUpdateCronRequestDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 任务管理控制器
 *
 * 提供任务列表、手动运行、状态切换、Cron 更新等接口。
 * 路径参数 id 均放在 URL 末尾，符合规范。
 *
 * @author mwangli
 * @since 2026-03-10
 */
@Slf4j
@RestController
@RequestMapping("/api/jobs")
@RequiredArgsConstructor
public class JobAdminController {

    private final JobSchedulerService jobSchedulerService;
    private final JobConfigRepository jobConfigRepository;

    /**
     * 获取任务列表
     */
    @GetMapping
    public ResponseDTO<List<JobConfig>> listJobs() {
        log.info("获取任务列表");
        return ResponseDTO.success(jobConfigRepository.findAll());
    }

    /**
     * 手动运行指定任务
     *
     * @param id 任务 ID
     */
    @PostMapping("/run/{id}")
    public ResponseDTO<Void> runJob(@PathVariable Long id) {
        log.info("手动运行任务: id={}", id);
        jobSchedulerService.runJobNow(id);
        return ResponseDTO.success(null);
    }

    /**
     * 切换任务启用状态
     *
     * @param id      任务 ID
     * @param payload 请求体，包含 active 字段
     */
    @PostMapping("/status/{id}")
    public ResponseDTO<Void> toggleStatus(
            @PathVariable Long id,
            @RequestBody(required = true) JobToggleStatusRequestDto payload) {
        log.info("切换任务状态: id={}, active={}", id, payload.isActive());
        jobSchedulerService.toggleJobStatus(id, payload.isActive());
        return ResponseDTO.success(null);
    }

    /**
     * 更新任务 Cron 表达式
     *
     * @param id      任务 ID
     * @param payload 请求体，包含 cron 字段
     */
    @PostMapping("/cron/{id}")
    public ResponseDTO<Void> updateCron(
            @PathVariable Long id,
            @RequestBody(required = true) JobUpdateCronRequestDto payload) {
        log.info("更新任务 Cron: id={}, cron={}", id, payload.getCron());
        jobSchedulerService.updateJobCron(id, payload.getCron());
        return ResponseDTO.success(null);
    }
}
