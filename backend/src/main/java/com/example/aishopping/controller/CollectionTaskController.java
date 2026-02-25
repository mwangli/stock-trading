package com.example.aishopping.controller;

import com.example.aishopping.entity.CollectionTask;
import com.example.aishopping.service.CollectionTaskService;
import com.example.aishopping.service.collector.ProductCollectorService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 采集任务控制器
 */
@RestController
@RequestMapping("/api/collection-tasks")
@Slf4j
public class CollectionTaskController {

    @Autowired
    private CollectionTaskService collectionTaskService;

    @Autowired
    private ProductCollectorService productCollectorService;

    /**
     * 获取任务列表
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> list(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size) {

        // 简化实现：查询所有任务
        List<CollectionTask> tasks = collectionTaskService.list();

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("data", tasks);
        response.put("total", tasks.size());

        return ResponseEntity.ok(response);
    }

    /**
     * 获取任务详情
     */
    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getById(@PathVariable Long id) {
        CollectionTask task = collectionTaskService.getById(id);

        Map<String, Object> response = new HashMap<>();
        if (task != null) {
            response.put("success", true);
            response.put("data", task);
        } else {
            response.put("success", false);
            response.put("message", "任务不存在");
        }

        return ResponseEntity.ok(response);
    }

    /**
     * 创建手动采集任务
     */
    @PostMapping
    public ResponseEntity<Map<String, Object>> create(
            @RequestBody Map<String, Object> request) {

        String taskName = (String) request.get("taskName");
        String categoryFilter = (String) request.get("categoryFilter");
        Integer maxProducts = request.get("maxProducts") != null ?
                Integer.valueOf(request.get("maxProducts").toString()) : null;

        CollectionTask task = collectionTaskService.createManualTask(
                taskName, categoryFilter, maxProducts
        );

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "任务创建成功");
        response.put("data", task);

        return ResponseEntity.ok(response);
    }

    /**
     * 手动执行采集任务
     */
    @PostMapping("/{id}/execute")
    public ResponseEntity<Map<String, Object>> execute(@PathVariable Long id) {
        CollectionTask task = collectionTaskService.getById(id);

        Map<String, Object> response = new HashMap<>();
        if (task == null) {
            response.put("success", false);
            response.put("message", "任务不存在");
            return ResponseEntity.ok(response);
        }

        if ("RUNNING".equals(task.getStatus())) {
            response.put("success", false);
            response.put("message", "任务正在执行中");
            return ResponseEntity.ok(response);
        }

        // 启动异步采集
        collectionTaskService.startTask(id);

        // 异步执行采集任务
        productCollectorService.collectProducts(task).thenAccept(result -> {
            // 任务完成后更新状态
            collectionTaskService.completeTask(id,
                    result.getSuccessCount(),
                    result.getFailedCount(),
                    result.getFilteredCount());
        });

        response.put("success", true);
        response.put("message", "任务已启动");
        response.put("data", task);

        return ResponseEntity.ok(response);
    }

    /**
     * 删除任务
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> delete(@PathVariable Long id) {
        boolean removed = collectionTaskService.removeById(id);

        Map<String, Object> response = new HashMap<>();
        response.put("success", removed);
        response.put("message", removed ? "删除成功" : "删除失败");

        return ResponseEntity.ok(response);
    }

    /**
     * 停止正在执行的任务
     */
    @PostMapping("/{id}/stop")
    public ResponseEntity<Map<String, Object>> stop(@PathVariable Long id) {
        CollectionTask task = collectionTaskService.getById(id);

        Map<String, Object> response = new HashMap<>();
        if (task == null) {
            response.put("success", false);
            response.put("message", "任务不存在");
            return ResponseEntity.ok(response);
        }

        if (!"RUNNING".equals(task.getStatus())) {
            response.put("success", false);
            response.put("message", "任务不在执行中");
            return ResponseEntity.ok(response);
        }

        // 更新任务状态为失败
        collectionTaskService.failTask(id, "用户手动停止");

        response.put("success", true);
        response.put("message", "任务已停止");

        return ResponseEntity.ok(response);
    }

    /**
     * 启用/禁用定时任务
     */
    @PostMapping("/{id}/toggle")
    public ResponseEntity<Map<String, Object>> toggle(@PathVariable Long id) {
        CollectionTask task = collectionTaskService.getById(id);

        Map<String, Object> response = new HashMap<>();
        if (task == null) {
            response.put("success", false);
            response.put("message", "任务不存在");
            return ResponseEntity.ok(response);
        }

        Boolean isEnabled = task.getIsEnabled();
        task.setIsEnabled(isEnabled == null || !isEnabled);
        task.setUpdatedAt(LocalDateTime.now());
        collectionTaskService.updateById(task);

        response.put("success", true);
        response.put("message", task.getIsEnabled() ? "任务已启用" : "任务已禁用");
        response.put("data", task);

        return ResponseEntity.ok(response);
    }

    /**
     * 快速启动采集任务（直接创建并执行）
     */
    @PostMapping("/quick-start")
    public ResponseEntity<Map<String, Object>> quickStart(
            @RequestParam(required = false, defaultValue = "10") Integer maxProducts) {

        // 创建并立即执行采集任务
        String taskName = "快速采集任务-" + LocalDateTime.now().toString();
        CollectionTask task = collectionTaskService.createManualTask(taskName, null, maxProducts);

        // 启动任务
        collectionTaskService.startTask(task.getId());

        // 异步执行
        productCollectorService.collectProducts(task).thenAccept(result -> {
            collectionTaskService.completeTask(task.getId(),
                    result.getSuccessCount(),
                    result.getFailedCount(),
                    result.getFilteredCount());
        });

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "采集任务已启动");
        response.put("taskId", task.getId());

        return ResponseEntity.ok(response);
    }
}
