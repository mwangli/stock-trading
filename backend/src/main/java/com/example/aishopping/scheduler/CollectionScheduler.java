package com.example.aishopping.scheduler;

import com.example.aishopping.entity.CollectionTask;
import com.example.aishopping.service.CollectionTaskService;
import com.example.aishopping.service.collector.ProductCollectorService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 采集任务调度器
 * 负责定时检查和执行采集任务
 */
@Component
@Slf4j
public class CollectionScheduler {

    @Autowired
    private CollectionTaskService collectionTaskService;

    @Autowired
    private ProductCollectorService productCollectorService;

    /**
     * 每天0点检查并执行定时任务
     */
    @Scheduled(cron = "0 0 0 * * ?")
    public void checkScheduledTasks() {
        log.debug("检查定时采集任务...");

        try {
            List<CollectionTask> pendingTasks = collectionTaskService.findPendingScheduledTasks();

            for (CollectionTask task : pendingTasks) {
                log.info("发现待执行的定时任务: ID={}, 名称={}", task.getId(), task.getTaskName());

                // 启动任务
                collectionTaskService.startTask(task.getId());

                // 异步执行采集任务
                productCollectorService.collectProducts(task).thenAccept(result -> {
                    // 任务完成后更新状态
                    collectionTaskService.completeTask(task.getId(),
                            result.getSuccessCount(),
                            result.getFailedCount(),
                            result.getFilteredCount());

                    // 更新下次执行时间
                    collectionTaskService.updateNextRunTime(task.getId());
                });
            }

            if (!pendingTasks.isEmpty()) {
                log.info("已启动 {} 个定时采集任务", pendingTasks.size());
            }

        } catch (Exception e) {
            log.error("检查定时任务失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 每小时清理过期日志（保留7天）
     */
    @Scheduled(cron = "0 0 * * * ?")
    public void cleanupOldLogs() {
        log.info("开始清理过期采集日志...");
        // TODO: 实现日志清理逻辑
        // collectionLogMapper.deleteOldLogs(LocalDateTime.now().minusDays(7));
        log.info("清理完成");
    }


}
