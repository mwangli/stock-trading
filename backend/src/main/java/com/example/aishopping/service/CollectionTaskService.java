package com.example.aishopping.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.aishopping.entity.CollectionTask;
import com.example.aishopping.mapper.CollectionTaskMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 采集任务服务
 */
@Service
@Slf4j
public class CollectionTaskService extends ServiceImpl<CollectionTaskMapper, CollectionTask> {

    /**
     * 查询待执行的定时任务
     */
    public List<CollectionTask> findPendingScheduledTasks() {
        return baseMapper.selectPendingScheduledTasks(LocalDateTime.now());
    }

    /**
     * 更新任务状态
     */
    public boolean updateTaskStatus(Long taskId, String status) {
        CollectionTask task = new CollectionTask();
        task.setId(taskId);
        task.setStatus(status);
        task.setUpdatedAt(LocalDateTime.now());
        return updateById(task);
    }

    /**
     * 开始执行任务
     */
    public void startTask(Long taskId) {
        CollectionTask task = new CollectionTask();
        task.setId(taskId);
        task.setStatus("RUNNING");
        task.setStartedAt(LocalDateTime.now());
        task.setUpdatedAt(LocalDateTime.now());
        updateById(task);
        log.info("任务开始执行: ID={}", taskId);
    }

    /**
     * 完成执行任务
     */
    public void completeTask(Long taskId, int successCount, int failedCount, int filteredCount) {
        CollectionTask task = getById(taskId);
        if (task != null) {
            task.setStatus("COMPLETED");
            task.setCompletedAt(LocalDateTime.now());
            task.setSuccessCount(successCount);
            task.setFailedCount(failedCount);
            task.setFilteredCount(filteredCount);
            task.setActualCount(successCount + failedCount + filteredCount);

            // 计算执行时长
            if (task.getStartedAt() != null) {
                long seconds = java.time.Duration.between(task.getStartedAt(), task.getCompletedAt()).getSeconds();
                task.setDurationSeconds((int) seconds);
            }

            task.setUpdatedAt(LocalDateTime.now());
            updateById(task);
            log.info("任务执行完成: ID={}, 成功={}, 失败={}, 过滤={}",
                    taskId, successCount, failedCount, filteredCount);
        }
    }

    /**
     * 任务执行失败
     */
    public void failTask(Long taskId, String errorMessage) {
        CollectionTask task = new CollectionTask();
        task.setId(taskId);
        task.setStatus("FAILED");
        task.setErrorMessage(errorMessage);
        task.setCompletedAt(LocalDateTime.now());
        task.setUpdatedAt(LocalDateTime.now());
        updateById(task);
        log.error("任务执行失败: ID={}, 错误={}", taskId, errorMessage);
    }

    /**
     * 创建手动任务
     */
    public CollectionTask createManualTask(String taskName, String categoryFilter, Integer maxProducts) {
        CollectionTask task = new CollectionTask();
        task.setTaskName(taskName);
        task.setTaskType("MANUAL");
        task.setStatus("PENDING");
        task.setCategoryFilter(categoryFilter);
        task.setMaxProducts(maxProducts != null ? maxProducts : 100);
        task.setIsEnabled(true);
        task.setCreatedAt(LocalDateTime.now());
        task.setUpdatedAt(LocalDateTime.now());

        save(task);
        log.info("创建手动采集任务: ID={}, 名称={}", task.getId(), taskName);

        return task;
    }

    /**
     * 创建定时任务
     */
    public CollectionTask createScheduledTask(String taskName, String categoryFilter,
                                            Integer maxProducts, String cronExpression) {
        CollectionTask task = new CollectionTask();
        task.setTaskName(taskName);
        task.setTaskType("SCHEDULED");
        task.setStatus("PENDING");
        task.setCategoryFilter(categoryFilter);
        task.setMaxProducts(maxProducts != null ? maxProducts : 100);
        task.setCronExpression(cronExpression);
        task.setIsEnabled(true);
        task.setCreatedAt(LocalDateTime.now());
        task.setUpdatedAt(LocalDateTime.now());

        // 计算下次执行时间
        if (cronExpression != null && !cronExpression.isBlank()) {
            task.setNextRunTime(calculateNextRunTime(cronExpression));
        }

        save(task);
        log.info("创建定时采集任务: ID={}, 名称={}, Cron={}", task.getId(), taskName, cronExpression);

        return task;
    }

    /**
     * 根据Cron表达式计算下次执行时间
     */
    private LocalDateTime calculateNextRunTime(String cronExpression) {
        try {
            // 简单实现：解析常见Cron表达式
            // 格式: "秒 分 时 日 月 周"
            // 例如: "0 0 2 * * ?" = 每天凌晨2点

            String[] parts = cronExpression.split("\\s+");
            if (parts.length >= 3) {
                int hour = Integer.parseInt(parts[2]);
                LocalDateTime next = LocalDateTime.now().plusDays(1);
                next = next.withHour(hour).withMinute(0).withSecond(0);
                return next;
            }
        } catch (Exception e) {
            log.warn("解析Cron表达式失败: {}", cronExpression);
        }

        // 默认：1小时后
        return LocalDateTime.now().plusHours(1);
    }

    /**
     * 更新下次执行时间
     */
    public void updateNextRunTime(Long taskId) {
        CollectionTask task = getById(taskId);
        if (task != null && task.getCronExpression() != null) {
            LocalDateTime nextRunTime = calculateNextRunTime(task.getCronExpression());
            task.setNextRunTime(nextRunTime);
            task.setUpdatedAt(LocalDateTime.now());
            updateById(task);
        }
    }
}
