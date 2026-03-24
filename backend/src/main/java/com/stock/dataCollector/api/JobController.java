package com.stock.dataCollector.api;

import com.stock.dataCollector.domain.dto.JobListItemDto;
import com.stock.dataCollector.domain.dto.JobListResponseDto;
import com.stock.dataCollector.domain.dto.JobRunRequestDto;
import com.stock.dataCollector.domain.dto.JobOperationResponseDto;
import com.stock.dataCollector.service.StockDataService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

/**
 * 任务管理控制器
 * <p>
 * 对应前端 /api/job/* 接口，提供任务列表、手动运行、创建/更新/删除等。
 * </p>
 *
 * @author mwangli
 * @since 2026-03-10
 */
@Slf4j
@RestController
@RequestMapping("/api/job")
@RequiredArgsConstructor
public class JobController {

    private final StockDataService stockDataService;

    /**
     * 获取任务列表
     */
    @GetMapping("/list")
    public ResponseEntity<JobListResponseDto> listJobs(
            @RequestParam(defaultValue = "1") int current,
            @RequestParam(defaultValue = "10") int pageSize) {
        log.info("[Job] 获取任务列表 | current={}, pageSize={}", current, pageSize);
        List<JobListItemDto> jobs = new ArrayList<>();

        // 1. 股票列表同步任务
        JobListItemDto job1 = JobListItemDto.builder()
                .id("1")
                .name("同步股票列表")
                .cron("0 0 1 * * SUN")
                .status("NORMAL")
                .description("每周日凌晨1点同步股票列表")
                .build();
        jobs.add(job1);

        // 2. 每日数据同步任务
        JobListItemDto job2 = JobListItemDto.builder()
                .id("2")
                .name("每日数据同步")
                .cron("0 0 18 * * MON-FRI")
                .status("NORMAL")
                .description("每个交易日18点同步日线数据")
                .build();
        jobs.add(job2);

        // 3. K线数据聚合任务
        JobListItemDto job3 = JobListItemDto.builder()
                .id("3")
                .name("K线数据聚合")
                .cron("0 30 18 * * MON-FRI")
                .status("NORMAL")
                .description("每个交易日18:30聚合周K和月K数据")
                .build();
        jobs.add(job3);

        JobListResponseDto response = JobListResponseDto.builder()
                .data(jobs)
                .total(jobs.size())
                .success(true)
                .build();

        return ResponseEntity.ok(response);
    }

    /**
     * 手动运行任务
     */
    @PostMapping("/run")
    public ResponseEntity<JobOperationResponseDto> runJob(@RequestBody(required = false) JobRunRequestDto params) {
        String jobId = params != null ? params.getId() : null;

        log.info("手动触发任务: {}", jobId);

        // 异步执行，不阻塞接口
        new Thread(() -> {
            if ("1".equals(jobId)) {
                stockDataService.syncStockList();
            } else if ("2".equals(jobId)) {
                log.info("每日数据同步任务已触发，请通过API接口触发");
            } else if ("3".equals(jobId)) {
                // K线数据聚合任务
                log.info("开始执行K线数据聚合任务...");
                StockDataService.AggregateResult result = stockDataService.reaggregateAllKLineData();
                log.info("K线数据聚合任务完成: {}", result);
            }
        }).start();

        JobOperationResponseDto response = JobOperationResponseDto.builder()
                .success(true)
                .message("任务已触发")
                .build();

        return ResponseEntity.ok(response);
    }

    @PostMapping("/create")
    public ResponseEntity<JobOperationResponseDto> create() {
        log.info("[Job] 创建任务 (Mock)");
        return ResponseEntity.ok(JobOperationResponseDto.builder().success(true).message("创建成功").build());
    }

    @PutMapping("/update")
    public ResponseEntity<JobOperationResponseDto> update() {
        log.info("[Job] 更新任务 (Mock)");
        return ResponseEntity.ok(JobOperationResponseDto.builder().success(true).message("更新成功").build());
    }

    @DeleteMapping("/delete")
    public ResponseEntity<JobOperationResponseDto> delete() {
        log.info("[Job] 删除任务 (Mock)");
        return ResponseEntity.ok(JobOperationResponseDto.builder().success(true).message("删除成功").build());
    }

    @PostMapping("/pause")
    public ResponseEntity<JobOperationResponseDto> pause() {
        log.info("[Job] 暂停任务 (Mock)");
        return ResponseEntity.ok(JobOperationResponseDto.builder().success(true).message("已暂停").build());
    }

    @PostMapping("/resume")
    public ResponseEntity<JobOperationResponseDto> resume() {
        log.info("[Job] 恢复任务 (Mock)");
        return ResponseEntity.ok(JobOperationResponseDto.builder().success(true).message("已恢复").build());
    }

    @PostMapping("/interrupt")
    public ResponseEntity<JobOperationResponseDto> interrupt() {
        log.info("[Job] 中断任务 (Mock)");
        return ResponseEntity.ok(JobOperationResponseDto.builder().success(true).message("已中断").build());
    }
}
