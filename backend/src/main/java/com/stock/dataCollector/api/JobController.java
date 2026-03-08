package com.stock.dataCollector.api;

import com.stock.dataCollector.service.StockDataService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 任务管理控制器
 * 对应前端 /api/job/* 接口
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
    public ResponseEntity<Map<String, Object>> listJobs(
            @RequestParam(defaultValue = "1") int current,
            @RequestParam(defaultValue = "10") int pageSize) {

        List<Map<String, Object>> jobs = new ArrayList<>();

        // 1. 股票列表同步任务
        Map<String, Object> job1 = new HashMap<>();
        job1.put("id", "1");
        job1.put("name", "同步股票列表");
        job1.put("cron", "0 0 1 * * SUN");
        job1.put("status", "NORMAL");
        job1.put("description", "每周日凌晨1点同步股票列表");
        jobs.add(job1);

        // 2. 每日数据同步任务
        Map<String, Object> job2 = new HashMap<>();
        job2.put("id", "2");
        job2.put("name", "每日数据同步");
        job2.put("cron", "0 0 18 * * MON-FRI");
        job2.put("status", "NORMAL");
        job2.put("description", "每个交易日18点同步日线数据");
        jobs.add(job2);

        // 3. K线数据聚合任务
        Map<String, Object> job3 = new HashMap<>();
        job3.put("id", "3");
        job3.put("name", "K线数据聚合");
        job3.put("cron", "0 30 18 * * MON-FRI");
        job3.put("status", "NORMAL");
        job3.put("description", "每个交易日18:30聚合周K和月K数据");
        jobs.add(job3);

        Map<String, Object> response = new HashMap<>();
        response.put("data", jobs);
        response.put("total", jobs.size());
        response.put("success", true);

        return ResponseEntity.ok(response);
    }

    /**
     * 手动运行任务
     */
    @PostMapping("/run")
    public ResponseEntity<Map<String, Object>> runJob(@RequestBody(required = false) Map<String, String> params) {
        String jobId = params != null ? params.get("id") : null;

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

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "任务已触发");

        return ResponseEntity.ok(response);
    }

    // Mock其他接口以满足前端调用
    @PostMapping("/create")
    public ResponseEntity<?> create() { return ResponseEntity.ok(Map.of("success", true)); }

    @PutMapping("/update")
    public ResponseEntity<?> update() { return ResponseEntity.ok(Map.of("success", true)); }

    @DeleteMapping("/delete")
    public ResponseEntity<?> delete() { return ResponseEntity.ok(Map.of("success", true)); }

    @PostMapping("/pause")
    public ResponseEntity<?> pause() { return ResponseEntity.ok(Map.of("success", true)); }

    @PostMapping("/resume")
    public ResponseEntity<?> resume() { return ResponseEntity.ok(Map.of("success", true)); }

    @PostMapping("/interrupt")
    public ResponseEntity<?> interrupt() { return ResponseEntity.ok(Map.of("success", true)); }
}
