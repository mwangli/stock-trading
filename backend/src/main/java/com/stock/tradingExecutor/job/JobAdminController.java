package com.stock.tradingExecutor.job;

import com.stock.dataCollector.domain.dto.ResponseDTO;
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
     * 获取任务列表（支持分页）
     *
     * @param pageNum  页码，默认为1
     * @param pageSize 每页大小，默认为10
     */
    @GetMapping
    public ResponseDTO<PageResult<JobConfig>> listJobs(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize) {
        log.info("获取任务列表，页码: {}, 每页大小: {}", pageNum, pageSize);
        
        // 构建分页查询
        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(
                pageNum - 1, // JPA页码从0开始
                pageSize
        );
        
        // 执行分页查询
        org.springframework.data.domain.Page<JobConfig> page = jobConfigRepository.findAll(pageable);
        
        // 构建分页结果
        PageResult<JobConfig> pageResult = new PageResult<>(
                page.getContent(),
                page.getTotalElements(),
                pageNum,
                pageSize
        );
        
        return ResponseDTO.success(pageResult);
    }

    /**
     * 手动运行指定任务
     *
     * @param id 任务 ID
     */
    @PostMapping("/{id}/run")
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
    @PostMapping("/{id}/status")
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
    @PostMapping("/{id}/cron")
    public ResponseDTO<Void> updateCron(
            @PathVariable Long id,
            @RequestBody(required = true) JobUpdateCronRequestDto payload) {
        log.info("更新任务 Cron: id={}, cron={}", id, payload.getCron());
        jobSchedulerService.updateJobCron(id, payload.getCron());
        return ResponseDTO.success(null);
    }
}
