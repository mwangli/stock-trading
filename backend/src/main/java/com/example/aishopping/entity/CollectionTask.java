package com.example.aishopping.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 采集任务实体类
 */
@Data
@TableName("collection_tasks")
public class CollectionTask {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("task_name")
    private String taskName;

    @TableField("task_type")
    private String taskType;

    @TableField("status")
    private String status;

    @TableField("category_filter")
    private String categoryFilter;

    @TableField("brand_filter")
    private String brandFilter;

    @TableField("max_products")
    private Integer maxProducts;

    @TableField("actual_count")
    private Integer actualCount;

    @TableField("success_count")
    private Integer successCount;

    @TableField("failed_count")
    private Integer failedCount;

    @TableField("filtered_count")
    private Integer filteredCount;

    @TableField("started_at")
    private LocalDateTime startedAt;

    @TableField("completed_at")
    private LocalDateTime completedAt;

    @TableField("duration_seconds")
    private Integer durationSeconds;

    @TableField("error_message")
    private String errorMessage;

    @TableField("cron_expression")
    private String cronExpression;

    @TableField("next_run_time")
    private LocalDateTime nextRunTime;

    @TableField("is_enabled")
    private Boolean isEnabled;

    @TableField("created_by")
    private String createdBy;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
